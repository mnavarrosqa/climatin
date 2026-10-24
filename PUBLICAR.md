# Publicar en Play Store

Qué hace falta para subir Climatin (teléfono y reloj) a Google Play.

## 1. Clave de firma propia

Se crea una sola vez y **no se puede perder**: sin ella no se pueden publicar actualizaciones (salvo que actives *Play App Signing*, que es lo recomendado).

```bash
keytool -genkeypair -v -keystore climatin.jks -alias climatin \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 climatin.jks > climatin.jks.b64   # en macOS: base64 -i climatin.jks
```

Guardá `climatin.jks` y las contraseñas fuera del repo (por ejemplo en un gestor de contraseñas).

## 2. Secretos en GitHub

En **Settings → Secrets and variables → Actions → New repository secret** del repo `climatin`:

| Secreto | Valor |
|---------|-------|
| `CLIMATIN_KEYSTORE_BASE64` | el contenido de `climatin.jks.b64` |
| `CLIMATIN_KEYSTORE_PASSWORD` | la contraseña del almacén |
| `CLIMATIN_KEY_ALIAS` | `climatin` |
| `CLIMATIN_KEY_PASSWORD` | la contraseña de la clave |

Con eso, cada push a `main` genera en **Actions → Artifacts → `climatin-play`** los dos paquetes firmados:

- `app-play-release.aab`: teléfono (variante "play", sin auto-actualización).
- `wear-release.aab`: reloj.

Sin los secretos se siguen generando, pero firmados con la clave de debug (sirven para probar, no para Play).

## 3. Play Console

1. Crear la app: nombre *Climatin*, idioma español (Latinoamérica), gratuita.
2. **Ficha de Play Store**: los textos están en `fastlane/metadata/android/es-419/`. Faltan las capturas (teléfono, y reloj si publicás Wear OS): sacalas desde el teléfono.
3. **Política de privacidad**: necesita una URL pública. Podés publicar `PRIVACIDAD.md` (por ejemplo como Gist público o en una página propia). Completá antes el email de contacto.
4. **Seguridad de los datos**: la app recopila *ubicación aproximada*, para *funcionalidad de la app*, no se comparte con terceros para publicidad, se transmite cifrada y no se puede pedir que se borre porque no se guarda en servidores.
5. **Ubicación en segundo plano**: Play exige una declaración y un video corto mostrando para qué se usa (widgets y fondo animado que siguen tu ubicación). Si preferís evitar ese trámite, se puede quitar el permiso `ACCESS_BACKGROUND_LOCATION` del manifiesto y la opción en ajustes.
6. **Clasificación de contenido** y **público objetivo**: completar los cuestionarios (app de clima, sin contenido sensible).
7. **Versiones**: subir `app-play-release.aab` a *Prueba interna* primero. Para el reloj, en *Configuración avanzada → Factores de forma* agregar Wear OS y subir `wear-release.aab` en su propio track.

## Números de versión

Cada paquete subido necesita un `versionCode` mayor que el anterior. El del teléfono está en `app/build.gradle.kts` y el del reloj en `wear/build.gradle.kts` (empieza en 2.000.000 para no chocar con el del teléfono).
