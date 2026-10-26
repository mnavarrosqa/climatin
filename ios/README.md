# Climatin para iPhone

Primera versión nativa (SwiftUI, iOS 17) de Climatin. Usa el mismo pronóstico de Open-Meteo que la app de Android: la lógica de `core/.../data/` está portada a Swift en `Shared/`.

## Qué hay

- `project.yml`: el proyecto de Xcode, descripto para [XcodeGen](https://github.com/yonaskolb/XcodeGen). El `.xcodeproj` no se versiona.
- `Shared/`: modelos, cliente de Open-Meteo, códigos de clima, formatos, frase del resumen, lluvia de la próxima hora, luna y la caché (`WeatherStore`). Se compila en la app y en el widget.
- `Climatin/`: la app (ubicación, pantalla principal, lista de ciudades).
- `ClimatinWidget/`: widget chico, mediano y grande con la ciudad elegida en la app.
- `Config/`: los Info.plist y los entitlements (app group `group.com.mnavarrosqa.climatin`, para que el widget lea la caché de la app).

## Cómo se compila en CI

GitHub Actions corre en macOS (sin firma):

```sh
cd ios
brew install xcodegen
xcodegen generate
xcodebuild -project Climatin.xcodeproj -scheme Climatin \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

Sin firma el app group no existe: la app guarda la caché en su propia carpeta y funciona igual, pero el widget no ve esos datos. Con la firma real se comparten.

## Para publicar en TestFlight (más adelante)

1. **Team ID** de la cuenta de Apple Developer, como secreto de GitHub (por ejemplo `APPLE_TEAM_ID`), pasado a `xcodebuild` como `DEVELOPMENT_TEAM`.
2. **Clave de API de App Store Connect** (Users and Access > Integrations): el Issuer ID, el Key ID y el archivo `.p8` como secretos de GitHub. Con eso `xcodebuild -allowProvisioningUpdates` firma y `xcrun altool` (o fastlane) sube el build.
3. **Registrar en la cuenta de desarrollador**:
   - los bundle ids `com.mnavarrosqa.climatin` y `com.mnavarrosqa.climatin.widget`;
   - el app group `group.com.mnavarrosqa.climatin`, habilitado en los dos.
4. Crear la app en App Store Connect con el bundle id de la app.
5. Agregar un ícono (`AppIcon` en un asset catalog): sin él, App Store Connect rechaza el build.
