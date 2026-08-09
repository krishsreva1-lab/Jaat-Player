# Troubleshooting Report: Invalid Package and Playback Errors

I have analyzed the issues you're facing. Here is the identification of the problems and how to solve them.

## 1. "Invalid Package" Error during Update

### What is this problem?
This is a **Signature Mismatch**. Android uses "App Signing" to verify that an update comes from the same developer as the original app.

### Why did this happen?
When we updated your `build.gradle.kts` and `local.properties` earlier, we set up **new signing credentials** (a new password and alias). If the version currently on your phone was signed with a different key, Android will block the update to prevent an "unofficial" app from overwriting your data.

### Solution:
- **If you have the old key**: You must enter the *exact* same `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in your `local.properties` that were used for the old version.
- **If you don't have the old key**: You must **uninstall** the old version of Jaat Player from your phone first. Then, you can install the new version.
    - > [!CAUTION]
    - > Uninstalling will delete all your downloaded songs and app settings.

---

## 2. "Prove you are not a bot" & "Age Restricted" Errors

### What is this problem?
YouTube has implemented aggressive bot detection (called **BotGuard**) to prevent third-party apps from streaming music.

### Why did this happen?
Since you installed the app on a **new phone**, it has no "history" or "session" with YouTube. YouTube sees a completely new device trying to stream music and flags it as a bot or requires a login to verify age.

### Solution:

#### A. Log In (Most Reliable)
The permanent fix is to **Log In**.
1. Go to **Settings > Account**.
2. Log in with your YouTube/Google account.
This provides a valid "Cookie" to YouTube, which bypasses almost all bot checks and allows age-restricted songs to play.

#### B. Change Stream Client
If you don't want to log in, try changing the "Stream Client":
1. Go to **Settings > Player > Stream Client**.
2. Switch between **Android VR** and **Web Remix**.
    - **Android VR** is usually faster and has fewer bot checks.
    - **Web Remix** supports higher quality but is more likely to trigger "bot" errors.

#### C. Rotate Guest Session
I found a feature in your code called `BotDetectionMitigator`. If you get a bot error, the app is supposed to try and "Rotate" your session automatically. If it doesn't work, clearing the app's cache/data (or reinstalling) can sometimes generate a fresh "Visitor ID" that works for a while.

## Summary Checklist
1. **Uninstall the old app** to fix the "Invalid Package" error.
2. **Log in to your account** inside the app to fix the "Not a bot" and "Age restricted" errors.
