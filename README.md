# Workout Tracker

A simple Android app for progressive overload training.

- **Week planner** — pick training days (e.g. Mon/Wed/Fri) and a workout rotation (A → B → …) that
  continues across weeks. The home screen shows the week, which workout falls on each day, and each
  session's exercises inline.
- **Per-session progression** — log the weight for each exercise in a session; next time you do that
  workout, the target is last session's weight + that exercise's increment. Squat in A and squat in B
  progress independently.
- **Editable everything** — exercises (sets, reps, increment, starting weight), workouts, rotation order.
- **History** — week-by-week cards showing every session, whether you hit the target, and average body weight.
- **Body weight** — daily morning weigh-ins shown as a Mon–Sun calendar with weekly averages and week-over-week change.
- **Backup** — export/import all data as JSON (Settings → Backup).

**Stack:** Kotlin · Jetpack Compose (Material 3) · Room (SQLite) · Navigation Compose · minSdk 26

## Layout

| File | What it is |
|---|---|
| `data/Entities.kt` | Tables: `Workout`, `Exercise`, `WorkoutExercise` (join + order), `LogEntry` (weight per workout/exercise/day), `BodyWeight` |
| `data/WorkoutDao.kt` | SQL queries (Room generates the implementation) |
| `data/AppDatabase.kt` | DB setup, migrations, starter A/B program |
| `Progression.kt` | Target = last session of the same workout + increment |
| `Schedule.kt` | Training days + rotation continuing across weeks |
| `Weekly.kt` | Weekly body-weight averages, history ranges, date formatting |
| `Backup.kt` | JSON export/import format |
| `AppViewModel.kt` | State + actions the screens call |
| `ui/*Screen.kt` | This week (home), History, Body weight, Settings, Exercises, exercise history |

The pure logic (`Progression`, `Schedule`, `Weekly`, `Backup`) is covered by unit tests in `app/src/test`.

## Build & run

Requires JDK 17+ and the Android SDK (platform 35). Create `local.properties` with `sdk.dir=...`,
or open the project in Android Studio.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on a connected phone (USB or wireless debugging)
```

### Wireless debugging (Android 11+)

Developer options → Wireless debugging → *Pair device with pairing code*, then:

```bash
adb pair <ip>:<pairing-port>     # enter the 6-digit code
adb connect <ip>:<connect-port>  # port shown on the Wireless debugging screen
./gradlew installDebug
```
