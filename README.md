# Desarrollo de la aplicación ImageDownloader
**Alumno:** Cesar Uriel Aguiar Altamirano  
**Expediente:** A225204109  
**Lenguaje de programación:** Java  
**Versión:** JDK 17 

---

## Fase 2. Procesar el argumento y construir una URL

**¿Cuál es la diferencia entre representar la dirección de un recurso mediante URL y establecer una conexión?**

Una URL representa la dirección donde se encuentra un recurso en Internet, indicando información como el protocolo, el servidor y la ruta del archivo. Sin embargo, tener una URL no significa que ya exista comunicación con el servidor.

Establecer una conexión implica utilizar esa dirección para comunicarse realmente con el servidor y solicitar el recurso. En otras palabras, la URL indica dónde se encuentra algo, mientras que la conexión permite acceder a ese recurso.

## Fase 3. Establecer una conexión y recuperar HTML

**¿En qué se diferencian URLConnection y HttpURLConnection?**

URLConnection es una clase que permite configurar y establecer conexiones con diferentes tipos de recursos. Por otro lado, HttpURLConnection es una clase derivada que está especializada en las comunicaciones mediante los protocolos HTTP y HTTPS. Esta última permite consultar códigos de respuesta, métodos HTTP y controlar las redirecciones.

**¿Por qué se establecen tiempos máximos?**

Se establecen tiempos máximos para evitar que el programa permanezca esperando indefinidamente cuando un servidor tarda demasiado en responder o no está disponible. El tiempo de conexión limita cuánto se espera para establecer comunicación, mientras que el tiempo de lectura limita cuánto se espera para recibir los datos.

**¿Qué significa HTTP 404?**

El código HTTP 404 significa que el servidor fue localizado y recibió la solicitud, pero no encontró el recurso solicitado. Por ejemplo, puede ocurrir cuando se intenta acceder a una imagen que fue eliminada o cuya dirección es incorrecta.

**¿Qué sucede si la conexión funciona, pero el servidor devuelve un error?**

Significa que la comunicación con el servidor se estableció correctamente, pero la solicitud no pudo completarse como se esperaba. El servidor puede responder con códigos como 404 (recurso no encontrado) o 500 (error interno del servidor). Por eso no basta con comprobar que existe una conexión: también es necesario revisar el código de respuesta antes de procesar el contenido.

## Fase 4. Identificar referencias a imágenes

En esta fase se analiza el contenido HTML recibido para localizar las etiquetas `<img>` y obtener las direcciones especificadas en sus atributos `src`.

*Nota: En la información proporcionada no aparece una pregunta específica para esta fase.*

## Fase 5. Resolver direcciones relativas

**¿Qué ocurriría si se intentara descargar directamente logo.png sin considerar la URL base?**

El programa no sabría en qué servidor o directorio se encuentra el archivo `logo.png`. Una dirección relativa necesita utilizar la URL de la página HTML como referencia para construir la dirección completa.

Por ejemplo, si la página se encuentra en `https://ejemplo.com/imagenes/` y contiene `logo.png`, la dirección final sería `https://ejemplo.com/imagenes/logo.png`. Sin considerar la URL base, el programa podría intentar acceder a una dirección incorrecta y la descarga fallaría.

## Fase 6. Descargar y almacenar imágenes

**¿Por qué se utiliza InputStream en lugar de BufferedReader para las imágenes?**

Porque las imágenes son archivos binarios y no texto. InputStream permite leer directamente los bytes recibidos y almacenarlos sin modificar su contenido.

BufferedReader está diseñado para leer información textual y utiliza una codificación de caracteres. Si se utilizara para procesar una imagen, algunos bytes podrían interpretarse incorrectamente y el archivo terminaría dañado. Por eso, para descargar imágenes se utiliza InputStream.

## Fase 7. Integración de la aplicación

### Resultados de las pruebas funcionales

Durante las pruebas se verificaron diferentes situaciones para comprobar que la aplicación pudiera procesar páginas HTML, resolver direcciones, descargar imágenes y manejar errores sin detener toda la ejecución.

| Caso | Entrada o condición | Resultado |
|---|---|---|
| P01 | Sin argumentos | PASA |
| P02 | URL inválida | PASA |
| P03 | Protocolo no admitido (ftp, file) | PASA |
| P04 | HTML sin imágenes | PASA |
| P05 | URL absoluta de imagen | PASA |
| P06 | URL relativa de imagen | PASA |
| P07 | Imagen duplicada (una sola descarga) | PASA |
| P08 | Imagen inexistente: error y continúa | PASA |
| P09 | Recurso que no es imagen | PASA |
| P09b | Referencias data: y javascript: rechazadas | PASA |
| P10 | Servidor no disponible | PASA |
| P11 | Archivo mayor al límite cancelado, sin parciales | PASA |
| P12 | Archivo existente: no se sobrescribe | PASA |
| X01 | Redirección válida (302) seguida | PASA |
| X02 | Redirección a esquema no admitido rechazada | PASA |
| X03 | Bucle de redirecciones limitado | PASA |
| X04 | Página que no es HTML rechazada | PASA |
| X05 | Informe descargas.csv con 4 columnas | PASA |

---

# Preguntas de reflexión

**1. ¿Qué responsabilidad desempeña la aplicación dentro de una arquitectura cliente-servidor?**

La aplicación desempeña el papel de cliente, ya que es quien inicia las solicitudes hacia el servidor. Primero solicita una página HTML, después analiza su contenido para localizar las imágenes y finalmente realiza nuevas solicitudes para descargarlas y almacenarlas localmente.

El servidor, por su parte, se encarga de recibir las solicitudes y proporcionar los recursos disponibles. Por lo tanto, la aplicación administra el proceso de obtención y almacenamiento de la información.

**2. ¿Qué diferencias existen entre URL, URLConnection y HttpURLConnection?**

URL representa la dirección de un recurso y contiene los datos necesarios para localizarlo, pero por sí sola no establece comunicación.

URLConnection permite configurar y realizar una conexión con el recurso indicado. HttpURLConnection es una implementación especializada en HTTP y HTTPS que permite trabajar con métodos de solicitud, códigos de respuesta y redirecciones.

En conjunto, estas clases permiten pasar de identificar un recurso a comunicarse con el servidor para obtenerlo.

**3. ¿Qué sucede al invocar openConnection()?**

Al invocar openConnection() se crea un objeto que representa la conexión con el recurso indicado por la URL. Sin embargo, esto no significa necesariamente que la comunicación con el servidor ya haya comenzado.

Una ventaja de este procedimiento es que permite configurar previamente aspectos como los tiempos máximos de conexión y lectura, así como las cabeceras de la solicitud.

**4. ¿Qué operaciones provocan que la conexión se establezca realmente?**

La conexión puede establecerse al invocar connect() o al realizar operaciones que necesitan comunicarse con el servidor, como getInputStream() o getResponseCode().

Estas operaciones provocan que se envíe la solicitud y se comience a recibir información. Por eso es importante configurar los parámetros necesarios antes de iniciar la comunicación.

**5. ¿Por qué una página HTML puede requerir múltiples conexiones?**

Porque una página HTML puede contener referencias a diferentes recursos, como imágenes, archivos CSS y scripts. Estos elementos no necesariamente están almacenados en el mismo servidor que la página principal.

En el caso de ImageDownloader, primero se obtiene el documento HTML y después se realiza una solicitud independiente por cada imagen que se desea descargar.

**6. ¿Cuál es la diferencia entre getInputStream() y getContentType()?**

getInputStream() permite acceder al contenido de la respuesta en forma de bytes para leerlo o almacenarlo.

En cambio, getContentType() obtiene el tipo de contenido indicado por el servidor mediante la cabecera Content-Type. Por ejemplo, puede devolver valores como image/png o text/html.

La diferencia principal es que uno permite leer el contenido del recurso y el otro proporciona información sobre su formato.

**7. ¿Por qué distinguir recursos textuales y binarios?**

Es importante distinguirlos porque no todos los recursos deben procesarse de la misma manera. Los archivos de texto necesitan interpretarse mediante una codificación de caracteres, mientras que los archivos binarios deben conservar exactamente sus bytes originales.

Por ejemplo, el HTML puede leerse mediante BufferedReader, pero una imagen debe descargarse mediante InputStream. Si se interpreta una imagen como texto, sus datos pueden alterarse y el archivo puede quedar corrupto.

**8. ¿Para qué sirven los tiempos máximos de conexión y lectura?**

Sirven para controlar cuánto tiempo puede esperar el programa durante una comunicación con el servidor.

El tiempo máximo de conexión limita la espera para establecer la comunicación, mientras que el tiempo máximo de lectura controla la espera para recibir información. Esto evita que una solicitud lenta o un servidor que no responde mantengan bloqueada la aplicación indefinidamente.

**9. ¿Qué limitaciones tiene analizar HTML mediante expresiones regulares?**

Las expresiones regulares permiten localizar patrones específicos dentro del HTML, pero no interpretan completamente la estructura del documento.

Por ejemplo, pueden tener dificultades con etiquetas mal formadas, atributos sin comillas, comentarios o contenido generado mediante JavaScript. También pueden dejar fuera características como srcset o la etiqueta base.

Por lo tanto, aunque son útiles para una implementación sencilla como esta práctica, un analizador HTML especializado sería más adecuado para procesar documentos complejos.

**10. ¿Cómo podría implementarse la descarga concurrente usando únicamente la API estándar de Java?**

Se podría implementar utilizando las herramientas del paquete java.util.concurrent, especialmente ExecutorService y Callable.

La idea sería crear un grupo limitado de hilos que permita descargar varias imágenes al mismo tiempo, en lugar de esperar a que termine una descarga para comenzar la siguiente.

Cada tarea se encargaría de descargar una imagen y devolver un resultado. Posteriormente, el programa recopilaría los resultados para generar el informe correspondiente.

También sería necesario controlar la cantidad de descargas simultáneas, evitar que dos tareas utilicen el mismo nombre de archivo y cerrar correctamente el ejecutor al terminar.

---


## Conclusión

Claro, ahora entiendo mejor cómo funciona la comunicación mediante HTTP y HTTPS. Descargar una imagen parecía algo sencillo, pero detrás hay solicitudes, respuestas, rutas y errores que deben manejarse correctamente.

Lo interesante fue comprobar que no basta con que el programa funcione; también hay que entender por qué funciona y qué hacer cuando algo falla. Al final, eso fue lo que más me dejó esta práctica.
