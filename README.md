#Cesar Uriel Aguiar ALtamirano
#Expediente a225204109
Fase 2. Procesar el argumento y construir una URL 
¿Cuál es la diferencia entre representar la dirección de un recurso mediante URL y establecer una conexión?

Fase 3. Establecer una conexión y recuperar HTML
¿En qué se diferencian URLConnection y HttpURLConnection? ¿Por qué se establecen tiempos máximos? ¿Qué significa HTTP 404? ¿Qué sucede si la conexión funciona, pero el servidor devuelve un error?

Fase 4. Identificar referencias a imágenes
echo

Fase 5. Resolver direcciones relativas
 ¿Qué ocurriría si se intentara descargar directamente logo.png sin considerar la URL base?
 
 
Fase 6. Descargar y almacenar imágenes
¿Por qué se utiliza InputStream en lugar de BufferedReader para las imágenes?

Fase 7. Integración de la aplicación
```text
Caso   Entrada o condición                                  Resultado
------------------------------------------------------------------------------
P01    Sin argumentos                                       PASA
P02    URL inválida                                         PASA
P03    Protocolo no admitido (ftp, file)                    PASA
P04    HTML sin imágenes                                    PASA
P05    URL absoluta de imagen                               PASA
P06    URL relativa de imagen                               PASA
P07    Imagen duplicada (una sola descarga)                 PASA
P08    Imagen inexistente: error y continúa                 PASA
P09    Recurso que no es imagen                             PASA
P09b   Referencias data: y javascript: rechazadas           PASA
P10    Servidor no disponible                               PASA
P11    Archivo mayor al límite cancelado, sin parciales     PASA
P12    Archivo existente: no se sobrescribe                 PASA
X01    Redirección válida (302) seguida                     PASA
X02    Redirección a esquema no admitido rechazada          PASA
X03    Bucle de redirecciones limitado                      PASA
X04    Página que no es HTML rechazada                      PASA
X05    Informe descargas.csv con 4 columnas                 PASA
------------------------------------------------------------------------------
```
## Preguntas de reflexión

**1. ¿Qué responsabilidad desempeña la aplicación dentro de una arquitectura cliente-servidor?**
Es el cliente: inicia las solicitudes, interpreta las respuestas y decide qué pedir después. Solicita el HTML, extrae las referencias, pide cada imagen y almacena el resultado. El servidor solo almacena los recursos y responde a lo que se le pide.

**2. ¿Qué diferencias existen entre `URL`, `URLConnection` y `HttpURLConnection`?**
`URL` solo representa la localización de un recurso (protocolo, servidor, puerto, ruta) y no se comunica. `URLConnection` es la conexión configurable con ese recurso, con acceso a cabeceras y contenido, independiente del protocolo. `HttpURLConnection` es su subclase para HTTP/HTTPS y añade el método, el código de respuesta y el control de redirecciones.

**3. ¿Qué sucede al invocar `openConnection()`?**
Se crea y devuelve el objeto de conexión, pero todavía no hay comunicación con el servidor. Eso permite configurarlo (tiempos máximos, método, cabeceras) antes de conectar. Para una URL http/https el objeto devuelto es un `HttpURLConnection`.

**4. ¿Qué operaciones provocan que la conexión se establezca realmente?**
`connect()` y cualquier operación que necesite datos del servidor: `getInputStream()`, `getResponseCode()`, `getContentType()` y el resto de consultas de cabeceras. Después de que se envía la solicitud ya no se pueden cambiar los parámetros de la conexión.

**5. ¿Por qué una página HTML puede requerir múltiples conexiones?**
Porque el HTML solo contiene referencias a otros recursos (imágenes, hojas de estilo, scripts). Cada recurso es una solicitud distinta y puede estar en otro servidor. Por eso la aplicación abre una conexión para el documento y una conexión independiente por cada imagen.

**6. ¿Cuál es la diferencia entre `getInputStream()` y `getContentType()`?**
`getInputStream()` da acceso al cuerpo de la respuesta, es decir, a los bytes del recurso. `getContentType()` devuelve un metadato de la cabecera `Content-Type` (por ejemplo `image/png`) y sirve para decidir cómo tratar esos bytes. Leer la cabecera no consume el cuerpo.

**7. ¿Por qué distinguir recursos textuales y binarios?**
El texto debe decodificarse con un juego de caracteres (`InputStreamReader` y `BufferedReader`); si se decodifica una imagen, los bytes que no son caracteres válidos se alteran y el archivo queda corrupto. Los recursos binarios deben copiarse byte a byte con un `InputStream` sin interpretar. Además, el texto necesita conocer su codificación y el binario no.

**8. ¿Para qué sirven los tiempos máximos de conexión y lectura?**
Sin ellos, un servidor caído o muy lento dejaría la aplicación bloqueada indefinidamente. `setConnectTimeout` limita la espera para establecer la conexión y `setReadTimeout` limita la espera entre datos recibidos. Al vencer lanzan `SocketTimeoutException`, que la aplicación captura para continuar con la siguiente imagen.

**9. ¿Qué limitaciones tiene analizar HTML mediante expresiones regulares?**
HTML no es un lenguaje regular: admite anidamiento, comentarios, scripts, atributos con o sin comillas y valores que contienen `>`. Una expresión regular puede fallar o encontrar etiquetas que el navegador no mostraría (por ejemplo dentro de un comentario). Tampoco ve lo que genera JavaScript ni interpreta `srcset` o `<base>`. Un analizador HTML real construye el árbol del documento como lo hace el navegador.

**10. ¿Cómo podría implementarse la descarga concurrente usando únicamente la API estándar de Java?**
Con `java.util.concurrent`: un `ExecutorService` con un número fijo de hilos (por ejemplo `Executors.newFixedThreadPool(4)`) al que se envía una tarea `Callable<Resultado>` por imagen, recogiendo los `Future` (o con `CompletableFuture`). Habría que asignar el número de archivo antes de lanzar las tareas para evitar colisiones de nombres, usar estructuras seguras entre hilos para el resumen, limitar el paralelismo para no saturar el servidor y cerrar el ejecutor con `shutdown()` y `awaitTermination()`. En Java 21 también existen los hilos virtuales (`Executors.newVirtualThreadPerTaskExecutor()`).
