# Streaks

A minimal, dark-themed habit tracker for Android with GitHub-style contribution grids, streaks and a home-screen widget.

- **App ID:** `com.diongue.streaks`
- **Min Android:** 8.0 (API 26) · **Target:** API 36
- **Stack:** Kotlin, Jetpack Compose (Material 3), Room, Glance (widget), Navigation Compose

## Features

- **Habits** with a name, colour and schedule: either *N times a week* (any days) or *specific weekdays*.
- **Check off today** from the list, the widget or a reminder notification.
- **Fill in past days** by tapping any square in the grid, or using the month calendar on a habit's detail screen.
- **Streaks.** Daily habits count days, weekly habits count weeks that hit the target. Today never breaks a streak until it's over.
- **Detail screen** with current and best streak, success rate, total check-ins, a month calendar and the full history grid.
- **Reminders.** An optional daily time per habit, skipped when the habit is already done, not due today, or the weekly target is met. The notification has a *Mark done* button.
- **Archive** habits to hide them but keep their history; restore them any time.
- **Drag to reorder** using the handle on each card.
- **Backup.** Export to and import from a JSON file (top-right menu). Android's cloud backup also includes the database.
- **Home-screen widget.** Resizable; it shows as many habits and recent days as fit, and tapping the circle toggles today.

## Project layout

```
app/src/main/java/com/diongue/streaks/
├── Habit.kt, HabitCompletion.kt   Room entities
├── HabitDao.kt, AppDatabase.kt    Database access and migrations
├── HabitStats.kt                  Schedules, streaks and success rates (pure Kotlin, unit-tested)
├── HabitViewModel.kt              UI state (derived from live database flows)
├── HabitActions.kt                Toggles shared by app, widget and notifications
├── HabitGrid.kt                   Heatmap, weekly progress bar, month calendar
├── HabitWidget.kt                 Glance widget + its toggle action
├── Reminders.kt                   Alarm scheduling, notification and boot receivers
├── Backup.kt                      JSON export/import format
├── MainActivity.kt                Navigation
└── ui/                            Screens, dialogs and theme
app/schemas/                       Exported Room schemas (used by migrations and tests)
```

## Building

Open the project in Android Studio, or use the command line with Android Studio's bundled JDK:

```sh
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # JVM unit tests (streaks, schedules, backup format)
./gradlew connectedAndroidTest   # instrumented tests (database migration), needs a device/emulator
```

### Release signing

The release keystore is kept **outside the project**, at `C:\Users\diong\keystores\streaks-release.jks`. Back this file up somewhere safe (a password manager or an encrypted drive). If you lose it, you can't publish updates to the app.

To sign release builds from Gradle:

1. Copy `keystore.properties.example` to `keystore.properties` in the project root.
2. Fill in the store password, key alias and key password.
3. Run `./gradlew assembleRelease`. The signed APK is written to `app/build/outputs/apk/release/`.

`keystore.properties`, `*.jks` and `*.keystore` are git-ignored. Without `keystore.properties`, release builds come out unsigned. Android Studio's *Build → Generate Signed App Bundle / APK* also works: point it at the keystore path above.

Release builds are minified and resource-shrunk with R8.

## Database changes

The database is versioned (currently **6**) and real user data is migrated, never wiped. When you change an entity:

1. Bump `version` in `AppDatabase`.
2. Add a `Migration(old, new)` and register it with `addMigrations(...)`.
3. Build once so the new schema JSON appears in `app/schemas/`, and commit it.
4. Add a case to `MigrationTest`.

## Moving data from the old app (`com.example.habitgrid`)

Earlier builds used the app ID `com.example.habitgrid`. Android treats a different ID as a different app, so Streaks starts empty and the old app's data stays where it is. To bring your history over:

1. Build this version under the old ID, signed with the **same key** as the installed old app:
   `./gradlew assembleRelease -PlegacyAppId`
2. Install it over the old app. It upgrades in place and keeps your data.
3. In that app, use **⋮ → Export backup**.
4. Install the normal build (`com.diongue.streaks`), then use **⋮ → Import backup** and pick the file.
5. Uninstall the old app.

## Widget notes

The widget watches the database directly, so changes made anywhere (widget, app or notification) show up straight away. It refreshes itself just after midnight and every 30 minutes as a fallback.
