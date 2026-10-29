# The iPhone app

The same app as the Android one. Every screen, the database and the season
sync come from `shared/`, the Kotlin Multiplatform module both phones share;
this folder holds only the few iPhone-specific pieces around them.

## What is in here

| File | What it is |
| --- | --- |
| `project.yml` | The Xcode project, written out rather than committed. CI runs `xcodegen generate` to turn it into `KCDiamonds.xcodeproj`. |
| `App/KCDiamondsApp.swift` | The whole Swift app: it shows the shared screens full screen and does nothing else. |
| `App/Assets.xcassets` | The app icon, built from the Android launcher icon's own artwork so both phones show the same mark. |
| `check_apple_keys.py` | Checks the Apple secrets the build needs, without printing them. |

There is no `.xcodeproj` in the repository on purpose: a generated project
cannot drift from what is described here, and it never produces a merge
conflict.

## Building it

Builds happen in GitHub Actions, on one of GitHub's Macs:

- **iOS TestFlight build** builds the app and uploads it to TestFlight for
  internal testing. Run it from the Actions tab; it also runs by itself when
  something in this folder changes on a branch. It defaults to Xcode 27.1,
  which is what lets the app fill the iPhone Duo's inner screen and use the
  wide layout — an app built with an older Xcode runs there in a phone-sized
  window and shows the phone layout instead.
- **Apple keys check** confirms the four secrets are the right ones, in about
  a minute, without printing any of them.

Nothing is signed in this repository. The archive is built unsigned, and
Apple signs it at upload with a certificate it holds for the team ("cloud
signing"), so there is no certificate or provisioning profile to store here
and no device to register.

## What has to exist first

**[APPLE-SETUP.md](APPLE-SETUP.md) walks through all of it, click by click** -
registering the App ID, creating the app record, making the API key, adding
the secrets and getting the first build onto a phone. The short version:

Four repository secrets, which you add in **Settings → Secrets and variables
→ Actions**. Never paste them into a chat or a file:

| Secret | Where it comes from |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer → Membership. Ten letters and digits. |
| `ASC_ISSUER_ID` | App Store Connect → Users and Access → Integrations → App Store Connect API. The long id above the key list. |
| `ASC_KEY_ID` | The same page: the key's own id. |
| `ASC_KEY_P8` | The `.p8` file that page downloads once. Paste the whole file, `BEGIN` and `END` lines included. |

And an app record in App Store Connect with the bundle id
`io.github.inspectorgad.kcdiamonds`. The keys check says plainly which of
these is missing.

## Opening it on a Mac

```sh
brew install xcodegen
cd ios && xcodegen generate
open KCDiamonds.xcodeproj
```

Xcode builds the shared Kotlin into `Shared.framework` through Gradle as a
step of its own build, so there is nothing to run first.
