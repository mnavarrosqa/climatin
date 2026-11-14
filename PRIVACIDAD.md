# Política de privacidad de Climatin

Última actualización: 14 de noviembre de 2026.

Climatin no tiene cuentas, publicidad ni analíticas, y no guarda datos tuyos en servidores propios.

## Ubicación

La app usa tu **ubicación aproximada** para mostrar el clima de donde estás. Si lo permitís, también la usa **con la app cerrada** para que los widgets, el fondo de pantalla animado y los avisos sigan tu ubicación. Si empezás **Salgo ahora**, la consulta cada 15 minutos durante las dos horas que dura, y deja de hacerlo al terminar.

Las coordenadas (redondeadas a unos 10 metros) se envían a:

- [Open-Meteo](https://open-meteo.com/en/terms) para el pronóstico, la calidad del aire y el buscador de ciudades.
- El geocodificador de tu teléfono (Google) para saber el nombre de la ciudad.

Para saber si está lloviendo ahora se pide a [RainViewer](https://www.rainviewer.com/) la imagen de radar de la región (un cuadrado de unos 300 km de lado, no tu ubicación exacta).

Si elegís **Foto de la capital** como fondo del pronóstico (Ajustes → Pantalla del clima → Fondo), las coordenadas redondeadas a unos 5 km se envían a [OpenStreetMap](https://osmfoundation.org/wiki/Privacy_Policy) (Nominatim) para saber en qué provincia o país estás, y a Wikidata, Wikipedia y Wikimedia Commons (de la [Fundación Wikimedia](https://foundation.wikimedia.org/wiki/Policy:Privacy_policy)) solo se les pide la foto libre de la capital de esa provincia o país (a Wikipedia, las imágenes del artículo de esa ciudad). No se envía nada más, y la foto queda guardada en el teléfono. Con el cielo animado (lo predeterminado) no se hace este pedido.

Para el mapa de lluvia se piden mapas de Esri (ArcGIS) y la lluvia de Open-Meteo para la zona que estás mirando. Las alertas se descargan completas del [Servicio Meteorológico Nacional](https://www.smn.gob.ar/) y se filtran en el teléfono, sin enviar tu ubicación.

Al **compartir el pronóstico** o guardarlo en la galería, la imagen se arma en el teléfono y solo va a la app que elijas o a tu carpeta Imágenes/Climatin.

## Datos guardados en el teléfono

Las ciudades que agregás, el último pronóstico y tus ajustes quedan solo en tu teléfono (o en tu reloj, para la app de Wear OS). Se borran al desinstalar la app.

## Notificaciones

Todos los avisos (lluvia, alertas del SMN, resumen diario, cambios bruscos, hora dorada, Salgo ahora y temperatura en la barra de estado) son opcionales y se generan en el teléfono con el pronóstico ya descargado. En Android 16 algunos pueden mostrarse "en vivo" en la barra de estado; eso tampoco envía datos a ningún lado.

## Actualizaciones

La versión que se instala desde fuera de la tienda consulta cada pocas horas la lista pública de versiones en GitHub para avisar si hay una nueva. Esa consulta no incluye tu ubicación ni ningún dato tuyo. La versión de Google Play no hace esta consulta: ahí actualiza la tienda.

## Contacto

Por dudas o pedidos sobre tus datos: martin@upgservicios.com
