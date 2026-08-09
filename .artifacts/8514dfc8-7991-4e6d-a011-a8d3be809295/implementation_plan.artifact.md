# Implementation Plan: Rename Echo Music to Jaat Player

This plan outlines the steps to rename the project from "Echo" / "Echo Music" to "Jaat" / "Jaat Player" across the codebase, including package names, module names, resource keys, and documentation.

## User Review Required

> [!IMPORTANT]
> - **Package Renaming**: The base package will be moved from `com.music.echo` to `com.krish.jaatplayer`. This will affect all source files.
> - **Deep Links**: Deep link schemes will change from `echomusic://` to `jaatplayer://` (or similar). This may affect existing external links.
> - **Module Renaming**: The `:echomusiccanvas` module will be renamed to `:jaatcanvas`.
> - **Logo**: I will look for the "red jaat player logo". If not found, I will use `ic_launcher_nobg.png` or ask for clarification.

## Proposed Changes

### [Core] Package and Namespace Renaming

#### [MODIFY] [build.gradle.kts](file:///D:/Studio Projects/Jaat-Player_v1.1/app/build.gradle.kts)
- Ensure `applicationId` and `namespace` are `com.krish.jaatplayer`.
- Update module dependency from `:echomusiccanvas` to `:jaatcanvas`.

#### [MODIFY] [settings.gradle.kts](file:///D:/Studio Projects/Jaat-Player_v1.1/settings.gradle.kts)
- Rename `:echomusiccanvas` to `:jaatcanvas`.

#### [MODIFY] [AndroidManifest.xml](file:///D:/Studio Projects/Jaat-Player_v1.1/app/src/main/AndroidManifest.xml)
- Update theme names (e.g., `Theme.echomusic` to `Theme.jaatplayer`).
- Update activity names with new package.
- Update intent-filter hosts and schemes (e.g., `echomusic` to `jaatplayer`).

### [Module] echomusiccanvas -> jaatcanvas

#### [RENAME] `echomusiccanvas` -> `jaatcanvas`
- Rename the directory.
- Update internal package names.

### [Resources] Strings and Drawables

#### [MODIFY] [strings.xml](file:///D:/Studio Projects/Jaat-Player_v1.1/app/src/main/res/values/strings.xml)
- Rename resource keys like `listen_on_echo_music` to `listen_on_jaat_player`.
- Update values where "Echo" or "Echo Music" is still present.

#### [MODIFY] [echo_strings.xml](file:///D:/Studio Projects/Jaat-Player_v1.1/app/src/main/res/values/echo_strings.xml)
- Rename file to `jaat_strings.xml` (optional, for consistency).
- Update content.

#### [RENAME] Drawable files
- `ic_qs_echo_logo.png` -> `ic_qs_jaat_logo.png`
- `echo_music_icon.xml` -> `jaat_music_icon.xml`
- `ic_echo_brain.xml` -> `ic_jaat_brain.xml`

### [Documentation] README and SETUP

#### [MODIFY] [README.md](file:///D:/Studio Projects/Jaat-Player_v1.1/README.md)
- Replace remaining "Echo" mentions.
- Fix logo and screenshot paths.
- Update build instructions.

#### [MODIFY] [SETUP.md](file:///D:/Studio Projects/Jaat-Player_v1.1/SETUP.md)
- Update repository names and paths.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project still builds.
- Check if package renaming caused any manifest merger issues.

### Manual Verification
- Verify that the app name in settings/launcher is "Jaat Player".
- Verify that deep links work with the new scheme (if possible to test).
- Check that the new logo is displayed in the Quick Settings tile and notifications.
