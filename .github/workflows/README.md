# Compilación automática de APK con GitHub Actions

Cada push a `main` (o ejecución manual desde la pestaña **Actions** → **Build Android APK** → **Run workflow**) compila el APK en la nube y lo deja descargable.

## Descargar el APK

1. Ve a tu repo en GitHub → pestaña **Actions**.
2. Abre la última ejecución de **Build Android APK**.
3. Al final de la página, sección **Artifacts**, descarga `AetherX-debug-apk`.
4. Descomprime el ZIP → tendrás `app-debug.apk`.
5. Pásalo al móvil e instálalo (permite "orígenes desconocidos").

El APK **debug** está firmado automáticamente con la clave de debug de Android — sirve para instalarlo en cualquier teléfono y probarlo. **No** sirve para Play Store.

## (Opcional) APK firmado para producción

Si en el futuro quieres un APK **release** firmado con tu propia clave (necesario para Play Store o distribución pública):

1. Genera un keystore en tu PC:
   ```
   keytool -genkey -v -keystore aetherx.keystore -alias aetherx -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Conviértelo a base64:
   ```
   base64 -w0 aetherx.keystore > keystore.b64
   ```
3. En GitHub: **Settings → Secrets and variables → Actions → New repository secret**, añade:
   - `ANDROID_KEYSTORE_BASE64` → contenido de `keystore.b64`
   - `ANDROID_KEYSTORE_PASSWORD` → contraseña del keystore
   - `ANDROID_KEY_ALIAS` → `aetherx`
   - `ANDROID_KEY_PASSWORD` → contraseña de la clave

El workflow detecta los secrets y genera también `AetherX-release-apk`.
