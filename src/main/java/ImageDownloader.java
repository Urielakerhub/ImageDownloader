import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageDownloader {

    public static URL validarURL(String argumento) {
        if (argumento == null || argumento.isBlank()) {
            throw new IllegalArgumentException("la dirección está vacía");
        }
        URI uri;
        try {
            uri = URI.create(argumento.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("formato no válido (" + e.getMessage() + ")");
        }
        String esquema = uri.getScheme();
        if (esquema == null
                || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(
                    "solo se admiten direcciones http y https (esquema: " + esquema + ")");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("no se indicó un servidor válido");
        }
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL no admitida: " + e.getMessage());
        }
    }

    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 15_000;
    static final int MAX_REDIRECTS = 5;
    static final long MAX_HTML_BYTES = 5L * 1024 * 1024;
    static final String USER_AGENT = "ImageDownloader/1.0 (practica academica)";

    private static final Pattern CHARSET_CABECERA =
            Pattern.compile("charset\\s*=\\s*\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_META =
            Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    record Pagina(URL urlFinal, int codigo, String tipoContenido, String html) { }

    @SuppressWarnings("deprecation")
    static HttpURLConnection abrirConexion(URL inicial) throws IOException {
        URL actual = inicial;
        for (int salto = 0; salto <= MAX_REDIRECTS; salto++) {
            URLConnection base = actual.openConnection();
            if (!(base instanceof HttpURLConnection http)) {
                throw new IOException("el recurso no usa HTTP/HTTPS: " + actual);
            }
            http.setRequestMethod("GET");
            http.setInstanceFollowRedirects(false);
            http.setConnectTimeout(CONNECT_TIMEOUT_MS);
            http.setReadTimeout(READ_TIMEOUT_MS);
            http.setRequestProperty("User-Agent", USER_AGENT);

            int codigo = http.getResponseCode();
            boolean redireccion = codigo == 301 || codigo == 302 || codigo == 303
                    || codigo == 307 || codigo == 308;
            if (!redireccion) {
                return http;
            }
            String destino = http.getHeaderField("Location");
            http.disconnect();
            if (destino == null || destino.isBlank()) {
                throw new IOException("redirección HTTP " + codigo + " sin encabezado Location");
            }
            URL siguiente;
            try {
                siguiente = new URL(actual, destino.trim());
            } catch (MalformedURLException e) {
                throw new IOException("redirección hacia una dirección inválida: " + destino);
            }
            String protocolo = siguiente.getProtocol().toLowerCase(Locale.ROOT);
            if (!protocolo.equals("http") && !protocolo.equals("https")) {
                throw new IOException("redirección hacia un esquema no admitido: " + protocolo);
            }
            if (actual.getProtocol().equalsIgnoreCase("https") && protocolo.equals("http")) {
                throw new IOException("redirección de https a http rechazada");
            }
            actual = siguiente;
        }
        throw new IOException("demasiadas redirecciones (máximo " + MAX_REDIRECTS + ")");
    }

    public static Pagina obtenerPagina(URL url) throws IOException {
        HttpURLConnection http = abrirConexion(url);
        try {
            int codigo = http.getResponseCode();
            if (codigo < 200 || codigo >= 300) {
                throw new IOException("el servidor respondió HTTP " + codigo);
            }
            String tipo = http.getContentType();
            if (tipo == null || !esHtml(tipo)) {
                throw new IOException("el recurso no es HTML (Content-Type: " + tipo + ")");
            }
            byte[] bytes;
            try (InputStream entrada = http.getInputStream()) {
                bytes = leerLimitado(entrada, MAX_HTML_BYTES);
            }
            Charset codificacion = detectarCodificacion(tipo, bytes);
            return new Pagina(http.getURL(), codigo, tipo, new String(bytes, codificacion));
        } finally {
            http.disconnect();
        }
    }

    public static String obtenerHTML(URL url) throws IOException {
        return obtenerPagina(url).html();
    }

    private static boolean esHtml(String tipo) {
        String mime = tipo.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return mime.equals("text/html") || mime.equals("application/xhtml+xml");
    }

    static byte[] leerLimitado(InputStream entrada, long maximo) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int leidos;
        while ((leidos = entrada.read(buffer)) != -1) {
            total += leidos;
            if (total > maximo) {
                throw new IOException("el documento supera el límite de " + maximo + " bytes");
            }
            salida.write(buffer, 0, leidos);
        }
        return salida.toByteArray();
    }

    static Charset detectarCodificacion(String tipo, byte[] bytes) {
        Matcher cabecera = CHARSET_CABECERA.matcher(tipo);
        if (cabecera.find()) {
            Charset c = charsetONulo(cabecera.group(1));
            if (c != null) {
                return c;
            }
        }
        String inicio = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.ISO_8859_1);
        Matcher meta = CHARSET_META.matcher(inicio);
        if (meta.find()) {
            Charset c = charsetONulo(meta.group(1));
            if (c != null) {
                return c;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset charsetONulo(String nombre) {
        try {
            return Charset.forName(nombre);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final Pattern ETIQUETA_IMG =
            Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATRIBUTO_SRC =
            Pattern.compile("(?<![\\w-])src\\s*=\\s*([\"'])(.*?)\\1",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static Set<String> extraerImagenes(String html) {
        Set<String> referencias = new LinkedHashSet<>();
        Matcher etiquetas = ETIQUETA_IMG.matcher(html);
        while (etiquetas.find()) {
            Matcher src = ATRIBUTO_SRC.matcher(etiquetas.group());
            if (src.find()) {
                String valor = src.group(2).trim().replace("&amp;", "&");
                if (!valor.isEmpty()) {
                    referencias.add(valor);
                }
            }
        }
        return referencias;
    }

    private static final Pattern ESQUEMA = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*):");

    @SuppressWarnings("deprecation")
    public static URL resolverImagen(URL urlPagina, String referencia) throws Exception {
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("referencia vacía");
        }
        String ref = referencia.trim().replace(" ", "%20");
        Matcher esquema = ESQUEMA.matcher(ref);
        if (esquema.find()) {
            String nombre = esquema.group(1).toLowerCase(Locale.ROOT);
            if (!nombre.equals("http") && !nombre.equals("https")) {
                throw new IllegalArgumentException("esquema no admitido: " + nombre + ":");
            }
        }
        URL resultado;
        try {
            resultado = new URL(urlPagina, ref);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("referencia no válida: " + referencia);
        }
        String protocolo = resultado.getProtocol().toLowerCase(Locale.ROOT);
        if (!protocolo.equals("http") && !protocolo.equals("https")) {
            throw new IllegalArgumentException("esquema no admitido: " + protocolo + ":");
        }
        if (resultado.getHost() == null || resultado.getHost().isBlank()) {
            throw new IllegalArgumentException("la referencia no indica un servidor");
        }
        return resultado;
    }

    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/jpg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp");

    record Resultado(boolean exito, Path archivo, String estado, long bytes) {
        static Resultado error(String estado) {
            return new Resultado(false, null, estado, 0);
        }
    }

    public static boolean descargarImagen(URL urlImagen, Path directorio, int numero) {
        return descargar(urlImagen, directorio, numero).exito();
    }

    static Resultado descargar(URL urlImagen, Path directorio, int numero) {
        HttpURLConnection http = null;
        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            http = abrirConexion(urlImagen);

            int codigo = http.getResponseCode();
            if (codigo < 200 || codigo >= 300) {
                return Resultado.error("Error HTTP " + codigo);
            }
            String tipo = http.getContentType();
            String mime = tipo == null ? "" : tipo.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!mime.startsWith("image/")) {
                return Resultado.error("No es una imagen (" + (tipo == null ? "sin tipo" : mime) + ")");
            }
            String extension = EXTENSIONES.get(mime);
            if (extension == null) {
                return Resultado.error("Tipo de imagen no admitido (" + mime + ")");
            }
            if (http.getContentLengthLong() > MAX_IMAGE_BYTES) {
                return Resultado.error("Excede 10 MB (Content-Length)");
            }

            temporal = Files.createTempFile(directorio, ".descarga_", ".part");
            long total = 0;
            try (InputStream entrada = http.getInputStream();
                 OutputStream salida = Files.newOutputStream(temporal)) {
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) {
                    total += leidos;
                    if (total > MAX_IMAGE_BYTES) {
                        return Resultado.error("Excede 10 MB (bytes recibidos)");
                    }
                    salida.write(buffer, 0, leidos);
                }
            }
            if (total == 0) {
                return Resultado.error("Respuesta vacía");
            }
            Path destino = moverSinSobrescribir(temporal, directorio, numero, extension);
            temporal = null;
            return new Resultado(true, destino, "Correcto", total);
        } catch (IOException | RuntimeException e) {
            return Resultado.error(describir(e));
        } finally {
            if (http != null) {
                http.disconnect();
            }
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignorada) {
                }
            }
        }
    }

    private static Path moverSinSobrescribir(Path temporal, Path directorio, int numero,
                                             String extension) throws IOException {
        for (int sufijo = 0; sufijo < 1000; sufijo++) {
            String nombre = String.format("imagen_%03d%s%s", numero,
                    sufijo == 0 ? "" : "_" + sufijo, extension);
            Path destino = directorio.resolve(nombre);
            try {
                return Files.move(temporal, destino);
            } catch (FileAlreadyExistsException existe) {
                continue;
            }
        }
        throw new IOException("no se encontró un nombre libre para la imagen " + numero);
    }

    static String describir(Exception e) {
        if (e instanceof SocketTimeoutException) {
            return "Tiempo de espera agotado";
        }
        if (e instanceof UnknownHostException) {
            return "Servidor no encontrado";
        }
        if (e instanceof ConnectException) {
            return "Conexión rechazada o servidor no disponible";
        }
        String mensaje = e.getMessage();
        return mensaje == null ? e.getClass().getSimpleName() : mensaje;
    }

    // ----- main de la fase -----

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java ImageDownloader <URL>");
            return;
        }

        try {
            Pagina pagina = obtenerPagina(validarURL(args[0]));
            Set<String> referencias = extraerImagenes(pagina.html());

            System.out.println("Imágenes encontradas: " + referencias.size());

            for (String referencia : referencias) {
                try {
                    URL imagen = resolverImagen(pagina.urlFinal(), referencia);
                    System.out.println(imagen);
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }

        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

}