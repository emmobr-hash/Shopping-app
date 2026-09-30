# Family Shopping

An Android app for keeping the family shopping list in one place.

## What it does

- **Shopping list** – add items, tick them off as you buy them, edit or delete them. Typing shows
  suggestions from things you've bought before, and things you buy a lot are offered as one-tap chips.
- **By supermarket** – every item can be tied to a store (Tesco, Aldi, …) or left as "Any store". The list is
  grouped by store and can be filtered to just the shop you're in. Manage the stores on the *Stores* tab.
- **Regular items**
  - *Every Sunday* – comes back onto the list each Sunday morning.
  - *Every N days / weeks / months* – e.g. toilet paper every 3 weeks. It appears on the list when it's due.
- **Sunday refresh** – at 06:00 every Sunday, ticked items are cleared and the weekly regulars are restored.
  Anything you didn't tick carries over. If the phone was off, the reset is applied the next time the app runs.
- **Special offers** – record deals you spot (product, price, store, end date). Offers matching something on
  your list, one of your regulars, or something you've bought 3+ times are highlighted, and shown under the
  item on the list. Expired offers tidy themselves away.
- **Share** – send the outstanding list (grouped by store) to WhatsApp or anywhere else.

## Not built yet

- **Sync between family phones.** The list currently lives on one device. Sharing it live needs a backend
  (e.g. Firebase Firestore); the data layer is isolated behind `ShoppingRepository` so this can be added.
  Until then, use the Share button.
- **Automatic offers.** No UK supermarket publishes a free public offers API, and scraping their sites is
  against their terms and breaks often, so offers are entered by hand for now.

## Project layout

| Path | What |
| --- | --- |
| `core/` | Plain Kotlin, unit-tested: the Sunday schedule, cadence planner, and item↔offer name matching |
| `app/` | Android app (Jetpack Compose, Room, WorkManager) |

## Building

The GitHub Actions workflow builds a debug APK on every push; download it from the run's *Artifacts*.

Locally (needs JDK 17 and the Android SDK):

```
./gradlew :core:test            # logic tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

On a machine without the Android SDK you can still run the logic tests with `./gradlew -PcoreOnly :core:test`.

To install the APK on your phone, copy it across (or open the artifact link on the phone) and allow
installs from your browser/file manager when prompted.
