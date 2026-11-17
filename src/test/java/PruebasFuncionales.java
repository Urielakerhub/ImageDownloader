import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Stream;


public class PruebasFuncionales {

    private static final long LIMITE = 10L * 1024 * 1024;
    private static final List<String[]> resultados = new ArrayList<>();
    private static int puerto;
    private static byte[] png;
    private static byte[] jpg;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        png = imagen("png");
        jpg = imagen("jpg");

        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        puerto = servidor.getAddress().getPort();
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.createContext("/", PruebasFuncionales::atender);
        servidor.start();

        String base = "http://127.0.0.1:" + puerto;
        Path trabajo = Files.createTempDirectory("imgdl_pruebas_");

        try {
            // P01
            Ejecucion e = ejecutar();
            comprobar("P01", "Sin argumentos", e.codigo == 2 && e.salida.contains("Uso: java ImageDownloader"), e);

            // P02
            e = ejecutar("esto no es una url");
            comprobar("P02", "URL inválida", e.codigo == 2 && e.salida.contains("inválido"), e);

            // P03
            e = ejecutar("ftp://example.com/archivo");
            boolean ftp = e.codigo == 2 && e.salida.contains("solo se admiten");
            Ejecucion f = ejecutar("file:///etc/passwd");
            comprobar("P03", "Protocolo no admitido (ftp, file)", ftp && f.codigo == 2 && f.salida.contains("solo se admiten"), e);

            // P04
            Path d4 = trabajo.resolve("p04");
            e = ejecutar(base + "/sin_imagenes.html", d4.toString());
            comprobar("P04", "HTML sin imágenes", e.salida.contains("Imágenes encontradas: 0")
                    && e.salida.contains("Descargas exitosas: 0"), e);

            // P05, P06, P07, P08, P09 sobre la misma ejecución
            Path d5 = trabajo.resolve("galeria");
            e = ejecutar(base + "/sitio/galeria.html", d5.toString());
            boolean ok2 = e.salida.contains("Descargas exitosas: 3") && e.salida.contains("Descargas fallidas: 4");
            comprobar("P05", "URL absoluta de imagen", ok2 && Files.exists(d5.resolve("imagen_002.jpg")), e);
            comprobar("P06", "URL relativa de imagen", ok2 && Files.exists(d5.resolve("imagen_001.png"))
                    && Files.exists(d5.resolve("imagen_003.png")), e);
            comprobar("P07", "Imagen duplicada (una sola descarga)", contarImagenes(d5) == 3
                    && e.salida.contains("Imágenes encontradas: 7"), e);
            comprobar("P08", "Imagen inexistente: error y continúa", e.salida.contains("Error HTTP 404")
                    && e.salida.contains("Descargas exitosas: 3"), e);
            comprobar("P09", "Recurso que no es imagen", e.salida.contains("No es una imagen"), e);
            comprobar("P09b", "Referencias data: y javascript: rechazadas",
                    e.salida.contains("esquema no admitido: data:")
                            && e.salida.contains("esquema no admitido: javascript:"), e);

            // P10: puerto cerrado
            int cerrado;
            try (ServerSocket s = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
                cerrado = s.getLocalPort();
            }
            e = ejecutar("http://127.0.0.1:" + cerrado + "/x.html", trabajo.resolve("p10").toString());
            comprobar("P10", "Servidor no disponible", e.codigo == 1
                    && e.salida.contains("No se pudo obtener la página") && !e.salida.contains("Exception"), e);

            // P11: archivos mayores al límite (sin Content-Length y con Content-Length)
            Path d11 = trabajo.resolve("p11");
            e = ejecutar(base + "/grandes.html", d11.toString());
            comprobar("P11", "Archivo mayor al límite cancelado, sin parciales",
                    e.salida.contains("Excede 10 MB (bytes recibidos)")
                            && e.salida.contains("Excede 10 MB (Content-Length)")
                            && e.salida.contains("Descargas exitosas: 1")
                            && contarImagenes(d11) == 1 && contarParciales(d11) == 0, e);

            // P12: archivo existente no se sobrescribe
            Path d12 = trabajo.resolve("p12");
            Files.createDirectories(d12);
            Files.writeString(d12.resolve("imagen_001.png"), "CONTENIDO ORIGINAL");
            e = ejecutar(base + "/sitio/galeria.html", d12.toString());
            comprobar("P12", "Archivo existente: no se sobrescribe",
                    Files.readString(d12.resolve("imagen_001.png")).equals("CONTENIDO ORIGINAL")
                            && Files.exists(d12.resolve("imagen_001_1.png")), e);

            // Extras de seguridad y de la actividad complementaria
            e = ejecutar(base + "/redir_ok", trabajo.resolve("x1").toString());
            comprobar("X01", "Redirección válida (302) seguida", e.salida.contains("Código HTTP: 200"), e);
            e = ejecutar(base + "/redir_ftp", trabajo.resolve("x2").toString());
            comprobar("X02", "Redirección a esquema no admitido rechazada",
                    e.codigo == 1 && e.salida.contains("esquema no admitido: ftp"), e);
            e = ejecutar(base + "/bucle", trabajo.resolve("x3").toString());
            comprobar("X03", "Bucle de redirecciones limitado", e.codigo == 1
                    && e.salida.contains("demasiadas redirecciones"), e);
            e = ejecutar(base + "/texto.txt", trabajo.resolve("x4").toString());
            comprobar("X04", "Página que no es HTML rechazada", e.codigo == 1 && e.salida.contains("no es HTML"), e);
            String csv = Files.readString(d5.resolve("descargas.csv"));
            comprobar("X05", "Informe descargas.csv con 4 columnas",
                    csv.startsWith("\"URL de imagen\",\"Archivo local\",\"Estado\",\"Tamaño (bytes)\"")
                            && csv.lines().count() == 8, e);
        } finally {
            servidor.stop(0);
        }

        System.out.println("=".repeat(78));
        System.out.printf("%-6s %-52s %s%n", "Caso", "Descripción", "Resultado");
        System.out.println("-".repeat(78));
        int fallos = 0;
        for (String[] r : resultados) {
            System.out.printf("%-6s %-52s %s%n", r[0], r[1], r[2]);
            if (!r[2].equals("PASA")) {
                fallos++;
            }
        }
        System.out.println("-".repeat(78));
        System.out.println("Casos: " + resultados.size() + "   Fallos: " + fallos);
        for (String[] r : resultados) {
            if (!r[2].equals("PASA")) {
                System.out.println("\n--- Salida del caso " + r[0] + " ---\n" + r[3]);
            }
        }
        System.exit(fallos == 0 ? 0 : 1);
    }

    // ---------- servidor de prueba ----------

    private static void atender(HttpExchange x) throws IOException {
        String ruta = x.getRequestURI().getPath();
        try {
            switch (ruta) {
                case "/sitio/galeria.html" -> enviar(x, 200, "text/html; charset=UTF-8", ("""
                        <html><body>
                          <img src="logo.png">
                          <img src="/img/foto.jpg" alt="foto">
                          <img SRC='http://127.0.0.1:%d/img/foto.jpg'>
                          <img src="../comun/relativa.png">
                          <img src="inexistente.png">
                          <img src="falsa.png">
                          <img src="data:image/png;base64,AAAA">
                          <img data-src="no_debe_usarse.png" src="javascript:alert(1)">
                        </body></html>
                        """).formatted(puerto).getBytes(StandardCharsets.UTF_8));
                case "/sitio/logo.png", "/comun/relativa.png" -> enviar(x, 200, "image/png", png);
                case "/img/foto.jpg" -> enviar(x, 200, "image/jpeg", jpg);
                case "/sitio/falsa.png" -> enviar(x, 200, "text/html", "<html>no soy una imagen</html>".getBytes());
                case "/sin_imagenes.html" -> enviar(x, 200, "text/html", "<html><body><p>Sin imágenes</p></body></html>".getBytes(StandardCharsets.UTF_8));
                case "/grandes.html" -> enviar(x, 200, "text/html", """
                        <img src="/pequena.png"><img src="/grande_chunked.png"><img src="/grande_cl.png">
                        """.getBytes());
                case "/pequena.png" -> enviar(x, 200, "image/png", png);
                case "/grande_chunked.png" -> {
                    x.getResponseHeaders().add("Content-Type", "image/png");
                    x.sendResponseHeaders(200, 0); // sin Content-Length
                    escribirGrande(x);
                }
                case "/grande_cl.png" -> {
                    x.getResponseHeaders().add("Content-Type", "image/png");
                    x.sendResponseHeaders(200, LIMITE + 1024); // Content-Length declarado
                    escribirGrande(x);
                }
                case "/redir_ok" -> redirigir(x, "/sin_imagenes.html");
                case "/redir_ftp" -> redirigir(x, "ftp://example.com/x");
                case "/bucle" -> redirigir(x, "/bucle");
                case "/texto.txt" -> enviar(x, 200, "text/plain", "hola".getBytes());
                default -> enviar(x, 404, "text/html", "<h1>404</h1>".getBytes());
            }
        } catch (IOException e) {
            // el cliente cerró la conexión (esperado en las descargas que exceden el límite)
        } finally {
            x.close();
        }
    }

    private static void enviar(HttpExchange x, int codigo, String tipo, byte[] cuerpo) throws IOException {
        x.getResponseHeaders().add("Content-Type", tipo);
        x.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream o = x.getResponseBody()) {
            o.write(cuerpo);
        }
    }

    private static void redirigir(HttpExchange x, String destino) throws IOException {
        x.getResponseHeaders().add("Location", destino);
        x.sendResponseHeaders(302, -1);
    }

    private static void escribirGrande(HttpExchange x) throws IOException {
        byte[] bloque = new byte[64 * 1024];
        try (OutputStream o = x.getResponseBody()) {
            for (long enviados = 0; enviados < LIMITE + 1024; enviados += bloque.length) {
                o.write(bloque);
            }
        }
    }

    // ---------- utilidades ----------

    record Ejecucion(int codigo, String salida) { }

    private static Ejecucion ejecutar(String... argumentos) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
                "-cp", System.getProperty("java.class.path"), "ImageDownloader"));
        cmd.addAll(List.of(argumentos));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Ejecucion(p.waitFor(), salida);
    }

    private static byte[] imagen(String formato) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                img.setRGB(i, j, (i * 16) << 16 | (j * 16) << 8 | 128);
            }
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, formato, o);
        return o.toByteArray();
    }

    private static long contarImagenes(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("imagen_")).count();
        }
    }

    private static long contarParciales(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".part")).count();
        }
    }

    private static void comprobar(String id, String descripcion, boolean ok, Ejecucion e) {
        resultados.add(new String[] {id, descripcion, ok ? "PASA" : "FALLA", e.salida});
    }
}
