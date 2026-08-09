# Implementation Plan - Natural Liquid Glass Effect

Correct the Liquid Glass effect to avoid automatic theme color adoption (blackish in dark mode, whitish in light mode) and provide a toggle for users to enable/disable this behavior.

## User Review Required

> [!NOTE]
> The "Natural" glass mode uses a neutral tint (neutral gray) instead of following the theme's surface color. This ensures the glass looks consistent regardless of the current light/dark mode setting.

## Proposed Changes

### Configuration & Preferences

#### [MODIFY] [PreferenceKeys.kt](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/kotlin/com/music/echo/constants/PreferenceKeys.kt)
- Add `LiquidGlassAdoptThemeColorKey` to persist the user preference.

#### [MODIFY] [strings.xml](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/res/values/strings.xml)
- Add `liquid_glass_adopt_theme_color` and `liquid_glass_adopt_theme_color_desc` strings.

### UI Components

#### [MODIFY] [GlassEffect.kt](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/kotlin/com/music/echo/ui/component/GlassEffect.kt)
- Update `GlassEffectConfig` to include `adoptThemeColor` property (defaulted to `true`).
- Update `Modifier.liquidGlass` to use a neutral gray (`0xFF888888`) when `adoptThemeColor` is disabled, instead of following the theme's luminance.

#### [MODIFY] [MainActivity.kt](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/kotlin/com/music/echo/MainActivity.kt)
- Collect the `liquidGlassAdoptThemeColor` preference from DataStore.
- Pass the preference value to the global `GlassEffectConfig`.

### Settings Screens

#### [MODIFY] [AppearanceSettings.kt](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/kotlin/com/music/echo/ui/screens/settings/AppearanceSettings.kt)
- Add a toggle for "Adopt Theme Color" under the Liquid Glass section.

#### [MODIFY] [GlassEffectSettings.kt](file:///D:/Studio Projects/Jaat-Player_v1/app/src/main/kotlin/com/music/echo/ui/screens/settings/GlassEffectSettings.kt)
- Add the same toggle to the detailed Liquid Glass settings screen for consistency.

## Verification Plan

### Manual Verification
- Navigate to **Settings > Appearance**.
- Locate the new **Adopt Theme Color** toggle under Liquid Glass.
- Toggle it off and verify that the glass effect in menus (e.g., Player three-dot menu if Liquid Glass is enabled for it) becomes neutral instead of blackish/whitish.
- Switch between Light and Dark themes and verify the "Natural" glass remains neutral.
- Toggle it back on and verify it correctly adopts dark/light colors again.
