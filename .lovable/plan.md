# Reconstruir Live Wallpaper Completo

Reviertir la dirección "APK vacía" y devolver al proyecto Android una arquitectura real de live wallpaper que renderice vídeo en el home screen, usando el `WallpaperService` oficial de Android + ExoPlayer sobre `SurfaceHolder`, expuesto a la UI React/Capacitor mediante un plugin nativo.

## Resultado final

- Pulsar el icono → abre la app Capacitor con la UI React (catálogo de wallpapers).
- Elegir un wallpaper → descarga el MP4 a `filesDir`.
- "Aplicar" → abre el selector nativo `ACTION_CHANGE_LIVE_WALLPAPER` apuntando a `AetherXLiveWallpaperService`.
- Confirmar en el sistema → el home screen renderiza el vídeo en bucle a pantalla completa, sin sonido, sin pantalla negra.

## Cambios en el proyecto

### 1. Identidad y configuración

- Mantener `applicationId` / `namespace` = `com.aetherx.localfinal` (no rompemos la firma instalada).
- `capacitor.config.ts`:
  - `webDir: "public/dist"` (build local, sin `server.url`).
  - Reactivar Capacitor Android (quitar `allowMixedContent: false` y `captureInput: false` que rompen el WebView, dejarlos en valores por defecto).
- `capacitor.plugins.json`: registrar el plugin local `AetherXLiveWallpaper`.

### 2. Plugin nativo `AetherXLiveWallpaperPlugin` (Java)

Recrear en `android/app/src/main/java/com/aetherx/localfinal/wallpaper/`:

- `AetherXLiveWallpaperPlugin.java` (`@CapacitorPlugin(name="AetherXLiveWallpaper")`):
  - `saveVideoFromUrl({url, fileName})` → descarga con redirects a `filesDir/wallpapers/<fileName>` y guarda la ruta del "último vídeo" en `SharedPreferences`.
  - `saveVideo({base64, fileName})` → variante base64.
  - `pickVideoFromDevice()` → `ACTION_OPEN_DOCUMENT` (video/*), copia a `filesDir`.
  - `applyHome()` / `applyLock()` / `applyBoth()` → llaman a `openPicker()`.
  - `openPicker()` → lanza `Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)` con `EXTRA_LIVE_WALLPAPER_COMPONENT` apuntando a `AetherXLiveWallpaperService`.
  - `checkCompatibility()` → reporta SDK, fabricante, si `WallpaperManager.isSetWallpaperAllowed()`, si hay vídeo, etc.

### 3. Servicio de live wallpaper

`AetherXLiveWallpaperService.java` extiende `android.service.wallpaper.WallpaperService`:

- `onCreateEngine()` devuelve `VideoEngine extends Engine`.
- `VideoEngine`:
  - Lee la ruta del último vídeo desde `SharedPreferences`.
  - En `onSurfaceCreated(SurfaceHolder)`: crea `ExoPlayer` (Media3), `setVideoSurface(holder.getSurface())`, `setRepeatMode(REPEAT_MODE_ALL)`, volumen 0, `prepare()`, `play()`.
  - En `onSurfaceChanged`: ajusta `setVideoScalingMode` a `VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING`.
  - En `onVisibilityChanged(false)`: pausa. En `true`: play.
  - En `onSurfaceDestroyed` / `onDestroy`: libera el `ExoPlayer`.
  - Manejo de excepciones: si el vídeo no carga, pinta el canvas en negro (no crash).

### 4. AndroidManifest

- Permisos:
  - `INTERNET`
  - `BIND_WALLPAPER` (uses-permission no es necesario; el `<service>` lo declara como `android:permission`).
- Dentro de `<application>`:
  - `MainActivity` queda como `BridgeActivity` de Capacitor (vuelve el WebView).
  - **Nuevo `<service>`:**
    ```xml
    <service
        android:name=".wallpaper.AetherXLiveWallpaperService"
        android:label="AetherX Live"
        android:permission="android.permission.BIND_WALLPAPER"
        android:exported="true">
        <intent-filter>
            <action android:name="android.service.wallpaper.WallpaperService" />
        </intent-filter>
        <meta-data
            android:name="android.service.wallpaper"
            android:resource="@xml/aetherx_wallpaper" />
    </service>
    ```
  - `FileProvider` para compartir el MP4 con el sistema cuando haga falta.
- Crear `res/xml/aetherx_wallpaper.xml` con `<wallpaper android:thumbnail="@mipmap/ic_launcher" android:description="@string/app_name" />`.

### 5. MainActivity

Reemplazar la `Activity` nativa "pantalla negra" por la `BridgeActivity` de Capacitor, manteniendo `applicationId` y `app_name`. La UI React vuelve a renderizar.

### 6. Dependencias Gradle

`android/app/build.gradle`:
- Reactivar `apply from: 'capacitor.build.gradle'` y restaurar `android/capacitor.settings.gradle` real (lo regenera `cap sync`).
- Añadir Media3 ExoPlayer:
  ```
  implementation "androidx.media3:media3-exoplayer:1.4.1"
  implementation "androidx.media3:media3-ui:1.4.1"
  implementation "androidx.media3:media3-common:1.4.1"
  ```
- Eliminar el bloqueo `verifyAetherXNativeLocalFinal` que prohíbe `Capacitor`, `WebView`, `BridgeActivity`, `startActivity(`, `ACTION_VIEW`. Reemplazar por una verificación reducida que solo comprueba:
  - applicationId/namespace siguen siendo `com.aetherx.localfinal`.
  - El `<service>` del wallpaper está declarado.
  - El APK final se llama `app-aetherx-localfinal-debug.apk`.
- Mantener `copyAetherXLocalFinalDebugApk` y `verifyAetherXFinalDebugApk` (renombrado del APK).

### 7. Script de "lock"

`scripts/lock-android-local-final.mjs`:
- Quitar la verificación que vacía `capacitor.plugins.json` y prohíbe `WebView`/`BridgeActivity`/`startActivity`.
- En su lugar: verificar que existen el plugin y el servicio, que el manifest declara el `<service>` con `BIND_WALLPAPER`, y que `applicationId` no ha mutado.
- Dejar de sobrescribir `MainActivity.java` desde `android-template/`.

### 8. Capa JS

`src/lib/native-wallpaper.ts` ya está preparada para esta API (saveVideoFromUrl, applyHome/Lock/Both, openPicker, pickVideoFromDevice, checkCompatibility). No hacen falta cambios funcionales; basta con que el plugin nativo vuelva a existir.

### 9. GitHub Actions

`.github/workflows/android-build.yml` ya espera `app-aetherx-localfinal-debug.apk`. Se mantiene.

## Ficheros tocados (resumen)

- **Modificar:** `capacitor.config.ts`, `android/app/build.gradle`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/java/com/aetherx/localfinal/MainActivity.java`, `android/app/src/main/assets/capacitor.config.json`, `android/app/src/main/assets/capacitor.plugins.json`, `scripts/lock-android-local-final.mjs`.
- **Crear:** `android/app/src/main/java/com/aetherx/localfinal/wallpaper/AetherXLiveWallpaperPlugin.java`, `AetherXLiveWallpaperService.java`, `android/app/src/main/res/xml/aetherx_wallpaper.xml`, `android/app/src/main/res/xml/file_paths.xml` (si falta).
- **Borrar:** `android-template/MainActivity.java` y `android-template/strings.xml` (ya no se usan como plantilla forzada).

## Cómo lo probarás tú

```
bun install
bun run build:android
cd android
./gradlew clean assembleDebug
```

APK resultante: `android/app/build/outputs/apk/debug/app-aetherx-localfinal-debug.apk`.

Instala, abre la app, elige un wallpaper, pulsa "Aplicar". Android abrirá el picker nativo de live wallpaper mostrando "AetherX Live"; al confirmar, el home reproducirá el vídeo en bucle.

## Riesgos / lo que romperá

- El APK ya no será "pantalla negra estática". La pantalla negra que pediste antes desaparece — vuelve la UI completa.
- Hay que volver a permitir tráfico HTTPS hacia `aetherx.org` para descargar los MP4 (o usar otra fuente). Esto contradice el bloqueo absoluto previo.
- El primer launch puede pedir permisos (almacenamiento / wallpaper) según versión de Android.
- Algunos fabricantes (Xiaomi/Huawei con MIUI/EMUI antiguos) restringen live wallpapers de terceros; en esos dispositivos el picker se abrirá pero el home puede ignorarlo. Lo loguearemos en `checkCompatibility()`.
