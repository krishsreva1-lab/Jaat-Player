# Implementation Plan - Apple-style Lock Screen Player

This plan outlines the steps to customize the media notification (lock screen player) to have bold Apple-style buttons and a blurred album art background.

## User Review Required

> [!WARNING]
> **Custom Background Limitations**: Android's system-level media control (the one in Quick Settings/Lock Screen on Android 11+) is managed by the OS. We cannot directly force a blurred background into the *system's* native media widget.
>
> **Proposed Solution**: I will implement a **Custom Notification Layout** using `RemoteViews`. This layout will appear on the lock screen and in the notification shade. It will feature:
> 1. A full-surface blurred background based on the current album art.
> 2. Bold Apple-style control buttons.
>
> **Note**: On Android 11+, you will see both the system's media control AND this custom notification unless the system media control is disabled in device settings.

## Proposed Changes

### Media Playback Utilities

#### [MODIFY] [MusicService.kt](file:///D:/Studio%20Projects/Jaat-Player_v1.1/app/src/main/kotlin/com/music/echo/playback/MusicService.kt)
- Update the custom layout button icons to use the "Apple" versions:
    - Play: `R.drawable.play_applemusic`
    - Pause: `R.drawable.pause_applemusic`
    - Skip Next: `R.drawable.apple_skip_next`
    - Skip Previous: `R.drawable.apple_skip_previous`

### Notification Provider

#### [NEW] [CustomNotificationLayout.kt](file:///D:/Studio%20Projects/Jaat-Player_v1.1/app/src/main/kotlin/com/music/echo/echomusic/updater/downloadmanager/CustomNotificationLayout.kt)
- Create a helper class to build `RemoteViews` for the notification.
- Include a layout with:
    - An `ImageView` for the blurred background art.
    - An `ImageView` for the clear album art thumbnail.
    - Large, bold control buttons.
    - Song title and artist text.

#### [MODIFY] [PlayerNotificationProvider.kt](file:///D:/Studio%20Projects/Jaat-Player_v1.1/app/src/main/kotlin/com/music/echo/echomusic/updater/downloadmanager/PlayerNotificationProvider.kt)
- Override `createNotification` to build a `Notification` using the new `RemoteViews`.
- Use `androidx.media.app.NotificationCompat.DecoratedMediaCustomViewStyle` to ensure basic media session integration.
- Implement a mechanism to load, blur, and cache the background artwork bitmap.

### Resources

#### [NEW] [notification_custom_player.xml](file:///D:/Studio%20Projects/Jaat-Player_v1.1/app/src/main/res/layout/notification_custom_player.xml)
- Define the XML layout for the custom notification.

## Verification Plan

### Automated Tests
- Build the app and check for resource or compilation errors.

### Manual Verification
- Play a song and lock the screen.
- Verify the notification shows a blurred background based on the song's art.
- Check that the buttons are large and bold.
- Verify that Play/Pause and Skip buttons work correctly from the notification.
