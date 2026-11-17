import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class ImageDownloader {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: java ImageDownloader <URL>");
            return;
        }
        try {
            URL url = validarURL(args[0]);
            int puerto = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
            String origen = url.getPort() != -1 ? "indicado" : "por defecto";
            System.out.println("Protocolo: " + url.getProtocol());
            System.out.println("Servidor: " + url.getHost());
            System.out.println("Puerto: " + puerto + " (" + origen + ")");
            System.out.println("Ruta: " + url.getPath());
        } catch (IllegalArgumentException e) {
            System.err.println("URL inválida: " + e.getMessage());
        }
    }


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
                    "solo se admiten direcciones http y https (esquema recibido: "
                            + (esquema == null ? "ninguno" : esquema) + ")");
        }
        String servidor = uri.getHost();
        if (servidor == null || servidor.isBlank()) {
            throw new IllegalArgumentException("no se indicó un servidor válido");
        }
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL no admitida: " + e.getMessage());
        }
    }
}