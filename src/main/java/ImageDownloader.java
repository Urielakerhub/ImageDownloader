import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarga las imágenes referenciadas mediante etiquetas {@code <img>} en una
 * página web. Todas las conexiones se realizan con {@link URL#openConnection()}
 * y {@link HttpURLConnection}; no se usan bibliotecas externas.
 */
public class ImageDownloader {

    // ===== FASE 2: validación del argumento =====

    /**
     * Convierte el argumento en una {@link URL}. Solo admite http y https y
     * exige un servidor. {@link URI} se usa únicamente para validar y convertir.
     *
     * @throws IllegalArgumentException si la dirección no es válida
     */
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

    /** Versión simplificada: devuelve solo el texto del HTML. */
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

    // ----- main de la fase -----
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java ImageDownloader <URL>");
            return;
        }
        try {
            Pagina pagina = obtenerPagina(validarURL(args[0]));
            System.out.println("Código HTTP: " + pagina.codigo());
            System.out.println("Tipo de contenido: " + pagina.tipoContenido());
            String html = pagina.html();
            System.out.println(html.substring(0, Math.min(500, html.length())));
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}