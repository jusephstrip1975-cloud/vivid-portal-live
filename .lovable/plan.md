# Plan definitivo AetherX Android

Causa raíz real (basada en los síntomas que describes):

1. **`KEY_VIDEO_PATH` se vuelve null entre builds** → se usa `apply()` (asíncrono) y el proceso del WallpaperService se reinicia antes de que SharedPreferences haya hecho fsync. Además no hay revalidación al arrancar el servicio.
2. **APK viejo "fantasma" tras instalar** → Gradle/Capacitor reutilizan `android/app/build`, `android/.gradle`, `assets/public` y el dispositivo a veces conserva data del package anterior. No hay forma visual de saber qué build está instalado.
3. **Play Protect bloquea** → el workflow genera un keystore "fallback" cuando faltan secrets, así que la firma cambia entre ejecuciones del runner (cada vez que el repo se clona limpio). Play Protect ve una firma nueva = app desconocida = bloqueo.
4. **Diagnóstico incompleto** → no expone versión, build id, hash de firma ni estado del servicio, así que no se puede distinguir un build viejo instalado de un bug real.

No son bugs separados — son consecuencias de **no tener un build reproducible y firmado de forma estable**.

---

## Cambios

### 1. Firma release estable (elimina Play Protect)

- Eliminar del workflow toda la rama "generar keystore temporal".
- El único keystore permitido es `android/app/release.keystore` ya commiteado (alias `aetherx-test`, password `aetherx-internal-test`).
- Si el keystore no existe o el alias no existe → **el build falla con mensaje claro**. Nunca se regenera.
- `signingConfigs.release` en `build.gradle` lee SIEMPRE de ese keystore (sin fallback a debug).
- `buildTypes.release` usa `signingConfig signingConfigs.release` siempre.
- Resultado: la firma SHA-256 es idéntica en todos los builds → Play Protect deja de avisar tras la primera aceptación.

### 2. Build reproducible y verificable

- `scripts/clean-android-total.mjs` ya limpia caches; añadir `node_modules/.vite` y `android/app/src/main/assets/capacitor.plugins.json`.
- Inyectar en `BuildConfig` tres campos: `AETHERX_BUILD_VERSION`, `AETHERX_BUILD_TIMESTAMP`, `AETHERX_BUILD_ID` (hash corto).
- Inyectar los mismos en el bundle web vía `define` de Vite → accesibles desde JS como `import.meta.env.VITE_AETHERX_BUILD_*`.
- Nuevo script `scripts/verify-apk.mjs` que tras `assembleRelease`:
  - calcula SHA-256 del APK,
  - extrae versionCode/versionName/signature con `apksigner` / `aapt`,
  - los escribe en `android/app/build/outputs/apk/release/BUILD_INFO.json`,
  - falla si la firma no coincide con la esperada.
- El workflow sube `BUILD_INFO.json` como artifact.

### 3. Persistencia del path del wallpaper (elimina null fantasma)

En `AetherXLiveWallpaperPlugin.java`:
- Tras escribir `current.mp4`: usar `editor.commit()` (síncrono) en lugar de `apply()`.
- Guardar en el mismo commit: `KEY_VIDEO_PATH`, `KEY_VIDEO_SIZE`, `KEY_VIDEO_SAVED_AT`, `KEY_LAST_SOURCE_URL`.
- Log obligatorio: `SAVE_VIDEO_SUCCESS`, `KEY_VIDEO_PATH_SAVED=<path>`.

En `AetherXLiveWallpaperService.java`:
- Al `onCreateEngine` y al `onVisibilityChanged(true)`:
  - leer `KEY_VIDEO_PATH`,
  - si es null pero `getExternalFilesDir(DIRECTORY_MOVIES)/AetherX/current.mp4` existe → reescribir el path en prefs con `commit()` y continuar (auto-recuperación).
  - log: `SERVICE_VIDEO_PATH_READ=...`, `SERVICE_VIDEO_RECOVERED=true|false`, `SERVICE_PLAYBACK_STARTED`.
- Mostrar "Archivo no encontrado" **solo** si tras la auto-recuperación el archivo realmente no existe.

### 4. Panel de diagnóstico real

`WallpaperDiagnosticPanel.tsx` y `native-wallpaper.ts` exponen:

```
APP_VERSION             BUILD_TIMESTAMP        BUILD_ID
APK_SIGNATURE_SHA256    PLUGIN_LOADED          SERVICE_RUNNING
VIDEO_PATH              VIDEO_EXISTS           VIDEO_SIZE
VIDEO_CAN_READ          LAST_NATIVE_EXCEPTION  LAST_JS_EXCEPTION
LAST_SERVICE_ERROR      INSTALL_SOURCE         SIGNATURE_VALID
```

Nuevo método nativo `getFullDiagnostic()` que devuelve todo en un objeto. Incluye `PackageManager.getPackageInfo(...).signingInfo` para SHA-256 real de la firma instalada. El JS también añade `BUILD_ID` y `BUILD_TIMESTAMP` desde `import.meta.env`.

El BUILD_ID se renderiza también como texto pequeño en la home (`src/routes/index.tsx`) → así, al abrir el APK, ves al instante si es el build nuevo.

### 5. Validación post-build (smoke test)

Añadir job en el workflow tras `assembleRelease`:
- `scripts/verify-apk.mjs` (firma + versionCode + tamaño).
- `grep` en el APK descomprimido para confirmar que `AetherXLiveWallpaperPlugin.class` y `AetherXLiveWallpaperService.class` existen.
- Si falla cualquiera → workflow rojo, no se sube artifact.

(No se puede ejecutar el plugin de verdad en CI sin emulador con Samsung — un instrumentation test daría falsos positivos. La verificación realista es: firma estable + clases presentes + assets presentes + diagnóstico visible en pantalla.)

### 6. Samsung One UI

- `AndroidManifest.xml`: confirmar `android:requestLegacyExternalStorage="false"` y que solo se usa `getExternalFilesDir()` (scoped storage compliant, no requiere permisos en Android 11+).
- Servicio: añadir `android:foregroundServiceType` no aplica a `WallpaperService` — confirmar que NO está marcado como foreground service (Samsung mata foreground services no declarados).
- En `onSurfaceCreated`: forzar `setFixedSize` al tamaño del surface antes de `prepare()` del player (workaround conocido One UI 6).

### 7. Limpieza de mensajes engañosos

- El plugin solo escribe `LAST_ERROR` cuando hay un `Throwable` real, no como fallback de cualquier estado vacío.
- El servicio NUNCA loggea "archivo no encontrado" si `new File(path).exists()` es true.
- El panel muestra `—` (no la cadena `"null"`) cuando un campo no aplica.

---

## Archivos a tocar

```
.github/workflows/android-build.yml      # eliminar fallback keystore, añadir verify
android/app/build.gradle                 # BuildConfig fields, signingConfig estricto
android/app/src/main/java/com/aetherx/livewallpaper/wallpaper/AetherXLiveWallpaperPlugin.java
android/app/src/main/java/com/aetherx/livewallpaper/wallpaper/AetherXLiveWallpaperService.java
android/app/src/main/java/com/aetherx/livewallpaper/MainActivity.java
android/app/src/main/AndroidManifest.xml # revisión scoped storage
src/lib/native-wallpaper.ts              # getFullDiagnostic
src/components/WallpaperDiagnosticPanel.tsx
src/routes/index.tsx                     # BUILD_ID visible
vite.config.ts                           # define VITE_AETHERX_BUILD_*
scripts/clean-android-total.mjs          # más rutas
scripts/verify-apk.mjs                   # NUEVO
```

Bump `versionCode` a `207`, `versionName` a `2.0.7-stable`.

---

## Qué NO voy a hacer

- No voy a generar otro keystore "por si acaso". Si el commiteado falla, el build falla y te lo digo.
- No voy a añadir más capas de fallback ocultas que enmascaren bugs.
- No voy a tocar el flujo de UI del selector (ya funciona).
- No voy a meter instrumentation tests en CI sin emulador Samsung (darían señal falsa).

¿Procedo con la implementación tal cual?
