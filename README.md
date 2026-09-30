# Family Shopping

An Android app for keeping the family shopping list in one place.

## What it does

- **Shared between phones** – everyone in the family sees the same list, live. It also works with no signal
  (in the shop): ticks and additions are saved on the phone and sync when you're back online.
- **Shopping list** – add items, tick them off as you buy them, edit or delete them. Typing shows
  suggestions from things you've bought before, and things you buy a lot are offered as one-tap chips.
- **By supermarket** – every item can be tied to a store or left as "Any store". A new family starts with
  Dunnes Stores, Aldi, Lidl and SuperValu; add, rename or remove them in *Settings*. The list is grouped by
  store and can be filtered to just the shop you're in.
- **Regular items**
  - *Every Sunday* – comes back onto the list each Sunday morning.
  - *Every N days / weeks / months* – e.g. toilet paper every 3 weeks. It appears on the list when it's due.
- **Sunday refresh** – at 06:00 every Sunday, ticked items are cleared and the weekly regulars are restored.
  Anything you didn't tick carries over. If the phones were off, the reset is applied the next time either
  app runs.
- **Share** – send the outstanding list (grouped by store) to WhatsApp or anywhere else.

## Setting up Firebase (one-off, ~10 minutes, free)

The shared list is stored in Google Firebase, so you need your own free project. The app builds without it
but shows a "Firebase isn't set up" screen until you finish this.

1. Go to <https://console.firebase.google.com>, **Add project**, and skip Google Analytics.
2. **Build > Authentication > Get started > Sign-in method**, enable **Anonymous**.
3. **Build > Firestore Database > Create database**. Pick a European location (e.g. `eur3` or
   `europe-west1`) and start in **production mode**. Then open the **Rules** tab, replace the contents with
   [`firestore.rules`](firestore.rules) from this repo and **Publish**.
4. **Project settings (cog) > Your apps > Android**. Use package name `com.family.shoppinglist`, register it,
   and download `google-services.json`.
5. Put that file in the `app/` folder, commit and push. (It identifies your project but isn't a password; the
   security rules above are what protect your data.) GitHub Actions then builds an APK with sharing switched on.

Then, on the first phone tap **Start a new family**. In *Settings* you'll see a family code; use **Send code**
to message it to the others, who tap **Join** and enter it. Anyone with the code can see and edit the list, so
only share it within the family.

## Project layout

| Path | What |
| --- | --- |
| `core/` | Plain Kotlin, unit-tested: the Sunday schedule, cadence planner, and item-name normalising |
| `app/` | Android app (Jetpack Compose, Firebase Firestore, WorkManager) |
| `firestore.rules` | Security rules to paste into Firebase |

## Building

The GitHub Actions workflow builds a debug APK on every push; download it from the run's *Artifacts*.

Locally (needs JDK 17 and the Android SDK):

```
./gradlew :core:test            # logic tests
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

On a machine without the Android SDK you can still run the logic tests with `./gradlew -PcoreOnly :core:test`.

To install the APK on a phone, copy it across (or open the artifact link on the phone) and allow installs
from your browser/file manager when prompted.
