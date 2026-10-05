# Chemopunk RPG

[![Android CI](https://github.com/Skalagrimmo/Chemopunk/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Skalagrimmo/Chemopunk/actions/workflows/android-ci.yml)

> Fallout 1 & 2 style 2.5D isometric ASCII RPG with Room database inventory, dynamic lighting, CRT scanlines, and tactical V.A.T.S. targeting.

ASCII-RPG у стилі Fallout 1/2: ізометрична 2.5D-графіка на ASCII-гліфах з OpenGL-рендерингом, процедурна генерація мап, динамічне освітлення з картами світла, CRT-скан-лінії, покрокові бої з тактичною системою прицілювання (V.A.T.S.), процедурний аудіо-двигун, крафт, інвентар на Room DB та нарративна система на Markdown.

## Tech stack

- **Language:** Kotlin 2.2, UI on Jetpack Compose
- **Build:** Gradle 9.6.0 (wrapper), AGP 9.1.1, KSP 2
- **Persistence:** Room (inventory + story databases)
- **Rendering:** custom OpenGL ASCII engine (isometric, dynamic lighting, sub-pixel glyph atlas, CRT shaders)
- **Testing:** JUnit 4 + Robolectric + Roborazzi (19 unit test classes)
- **Extras:** Firebase AI (declared, currently unused in code), Retrofit/Moshi/OkHttp

## Requirements

- JDK 17+ (recommended: JDK 21)
- Android SDK with platform **android-36** (compileSdk 36.1, minSdk 24, targetSdk 36)
- Android Studio (Meerkat/Narwhal or newer recommended) or a local Gradle to bootstrap the wrapper once

## Getting started

### Android Studio

1. **File → Open…** and select this repository's root folder.
2. Android Studio downloads the Gradle 9.6.0 distribution from `gradle/wrapper/gradle-wrapper.properties` and the required SDK components automatically.
3. Run the `app` configuration on a device or emulator (minSdk 24 / Android 7.0+).

### Command line

The Gradle wrapper scripts are committed, but the wrapper **jar** (`gradle/wrapper/gradle-wrapper.jar`) is not in the repository yet — the first CI run commits it automatically. If it is still missing locally, bootstrap once:

```bash
gradle wrapper --gradle-version 9.6.0   # any local Gradle 8.14+ / 9.x works
./gradlew assembleDebug
```

If `./gradlew` is not executable: `chmod +x gradlew` or run `sh ./gradlew …`.

Build outputs:

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests (JUnit + Robolectric + Roborazzi)
./gradlew connectedDebugAndroidTest  # instrumented tests (device/emulator)
```

### Windows (cmd)

```bat
gradlew.bat assembleDebug
```

## CI

`.github/workflows/android-ci.yml` builds the debug APK and runs the unit tests on every push/PR to `main`, uploads the APK as an artifact, and commits the generated Gradle wrapper (including `gradle-wrapper.jar`) back to the branch if it was missing.

## Secrets & signing

- **`google-services.json` is intentionally not required** — the `google-services` plugin is configured with `MissingGoogleServicesStrategy.WARN`, and Firebase/Gemini APIs are not used in code yet.
- **`.env`** (from `.env.example`) feeds the Secrets Gradle Plugin. `GEMINI_API_KEY` is currently commented out because the Gemini API is not used yet.
- **Signing** is fully environment-driven so fresh clones and CI always build:
  - Debug: uses `debug.keystore` in the repo root if present, otherwise AGP's default auto-generated debug keystore.
  - Release: set `KEYSTORE_PATH` (default `my-upload-key.jks` in the repo root), `STORE_PASSWORD`, `KEY_PASSWORD`; if absent, the release build falls back to debug signing instead of failing.
- Keystores, `.env` and `local.properties` are git-ignored and must never be committed.

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt            # entry point, audio lifecycle handling
├── data/                      # game models, Markdown parser, map generator
│   ├── narrative/             # MarkdownNarrativeParser (story scripts → DB)
│   └── room/                  # Room DBs: inventory + story/narrative
├── engine/                    # ASCII OpenGL engine: isometric renderer, dynamic
│                              # lighting, shaders, font/sub-pixel atlases,
│                              # NPC state machine, procedural audio
├── ui/
│   ├── GameScreen.kt          # main game screen
│   ├── components/            # HUD, D-pad, radial menu, log stream, modals,
│   │                          # combat queue, synthesis mini-game, etc.
│   └── theme/                 # Compose theme
└── viewmodel/                 # GameViewModel (game loop), StoryViewModel
app/src/main/assets/           # narrative content (Markdown world files)
app/src/test/                  # unit tests (Robolectric + Roborazzi)
```

## Roadmap to release

- [x] Buildable repository (wrapper, signing, README, CI)
- [ ] Green CI build of the debug APK
- [ ] All unit tests passing
- [ ] Playtest pass on a device (game loop, combat, inventory, audio)
- [ ] Optional: enable Firebase AI features (needs `google-services.json` + `.env` key)
- [ ] Release signing key + signed release APK/AAB

## License

All rights reserved (private project).
