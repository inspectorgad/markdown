# Setting up Apple and GitHub for the iPhone app

One-time setup. After this, every TestFlight build is one click in the
Actions tab.

Nothing here asks you to put a key into a chat, a file, or this repository.
The private key goes straight from Apple's download into GitHub's secrets box.

**Exact values this app uses — copy them carefully:**

| Thing | Value |
| --- | --- |
| Bundle ID | `io.github.inspectorgad.kcdiamonds` |
| App name on the phone | KC Diamonds |
| Scheme / product name | KCDiamonds |

---

## Before you start

You need a paid **Apple Developer Program** membership (about $99/year). The
free account cannot distribute through TestFlight. If you are not sure, sign
in at developer.apple.com and look for "Program Membership" — if you only see
an invitation to enroll, that has to happen first and can take a day or two.

On your own account you are the Account Holder, which has every permission
these steps need.

**You do not need to create any certificate or provisioning profile.** That is
usually the painful part of iOS setup and this project skips it: the build is
archived unsigned, and Apple signs it at upload with a certificate it keeps for
you ("cloud signing"). There is nothing to store, renew, or lose.

---

## Part 1 — Register the App ID

The bundle ID has to exist here before App Store Connect will let you pick it.

1. Go to **developer.apple.com/account** and sign in.
2. Click **Certificates, Identifiers & Profiles**.
3. Click **Identifiers** in the sidebar, then the **+** button at the top left.
4. Select **App IDs**, click **Continue**.
5. Leave the type as **App**, click **Continue**.
6. **Description:** `KC Diamonds Stats` (this is just a label for you).
7. Select **Explicit App ID** and enter the Bundle ID exactly:

       io.github.inspectorgad.kcdiamonds

8. **Capabilities: tick nothing.** This app sends no push notifications and
   uses no special services. (In-App Purchase may be ticked and greyed out by
   default — that is normal and harmless.)
9. Click **Continue**, check the summary, click **Register**.

---

## Part 2 — Create the app record in App Store Connect

1. Go to **appstoreconnect.apple.com** and sign in.
2. Click **Apps**, then the **+** at the top left, then **New App**.
3. Fill the dialog in:
   - **Platforms:** tick **iOS** only.
   - **Name:** `KC Diamonds Stats`. This has to be unique across the whole
     App Store, so if it is taken, use something like `KC Diamonds Softball`.
     It is only ever seen by you in TestFlight.
   - **Primary Language:** English (U.S.)
   - **Bundle ID:** pick `io.github.inspectorgad.kcdiamonds` from the dropdown.
     If it is not listed, Part 1 did not finish — go back and check.
   - **SKU:** any private string of your choosing. `kc-diamonds-stats` is fine.
     Nobody sees it; it is your own reference.
   - **User Access:** Full Access.
4. Click **Create**.

The app appears with status "Prepare for Submission". That is right. You are
never submitting it to the store — it only has to exist so TestFlight has
somewhere to put builds.

> If this step complains about agreements, the Account Holder has to accept
> the current agreements under **Business** first.

---

## Part 3 — Create the App Store Connect API key

This is the key that lets GitHub upload builds and sign them.

1. In App Store Connect, click **Users and Access**.
2. Click the **Integrations** tab. It opens on **App Store Connect API**.
3. Click **Team Keys** (not Individual Keys).
4. Click **Generate API Key** (or the **+**).
5. **Name:** `GitHub Actions — KC Diamonds`
6. **Access:** choose **Admin**.

> **Choose Admin, and read this before clicking Generate.** A key's access
> level *cannot be changed afterwards* — if it is too low you must revoke it,
> generate a new one, and replace the GitHub secret. Admin is what cloud
> signing needs to create the distribution certificate on your behalf.
> "Developer" is not enough.

7. Click **Generate**.
8. Now three pieces of information are on this page. Get all three:

   - **Issuer ID** — a long UUID like `69a6de7e-....-....-....-............`,
     shown above the key list. Click to copy it.
   - **Key ID** — ten characters, in the row of the key you just made.
   - **The key file** — click **Download API Key**. You get a file named
     `AuthKey_XXXXXXXXXX.p8`.

> **The download link appears once.** Apple keeps no copy. If you lose the
> file you must revoke the key and start Part 3 again. Save it somewhere safe
> (not in this repository, not in a chat) until Part 5 is done.

---

## Part 4 — Find your Team ID

1. Go to **developer.apple.com/account**.
2. Click **Membership details** in the sidebar.
3. Copy the **Team ID** — ten characters, letters and digits.

---

## Part 5 — Put the four values into GitHub

1. Go to **github.com/inspectorgad/markdown**.
2. **Settings** → in the sidebar, **Secrets and variables** → **Actions**.
3. On the **Secrets** tab, click **New repository secret** once per row below.
   The names must match exactly — they are case-sensitive.

| Name | Value |
| --- | --- |
| `APPLE_TEAM_ID` | The Team ID from Part 4 |
| `ASC_ISSUER_ID` | The Issuer ID from Part 3 |
| `ASC_KEY_ID` | The Key ID from Part 3 |
| `ASC_KEY_P8` | The **entire contents** of the `.p8` file |

For `ASC_KEY_P8`, open the `.p8` in a plain text editor and copy everything,
including the first and last lines:

    -----BEGIN PRIVATE KEY-----
    MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQg...
    ...
    -----END PRIVATE KEY-----

Paste the lot into the value box. The line breaks matter; do not join it into
one line and do not add quotes.

Once all four are saved you can delete your copy of the `.p8`, or keep it
somewhere private. GitHub will not show you a secret again after saving, but
you can always overwrite one.

---

## Part 6 — Check the keys before building anything

> **While this work is still on a branch there is no "Run workflow" button.**
> GitHub only shows it for workflows that are on the repository's default
> branch, and these are not on `main` yet. Until they are, both workflows
> below start on a push instead: `Apple keys check` whenever
> `ios/check_apple_keys.py` changes, and the TestFlight build whenever
> anything in `ios/` does. So for now, say the word and a one-line push will
> set either going. Once the branch is merged, the buttons appear and this
> part reads as written.

1. In the repo, go to the **Actions** tab.
2. Choose **Apple keys check** in the sidebar, then **Run workflow**.
3. It finishes in about a minute and prints a line per check.

Every line should read PASS, ending with something like:

    PASS  App Store Connect accepts the key
    PASS  the app exists in App Store Connect - "KC Diamonds Stats"
    PASS  the App ID is registered

It never prints a secret — only whether each one works. If a line says FAIL it
tells you which value is wrong and what it should look like.

---

## Part 7 — Build it to TestFlight

(Again: until the branch is merged there is no button — a push to `ios/`
starts it instead. See the note in Part 6.)

1. **Actions** tab → **iOS TestFlight build** → **Run workflow**.
2. Leave Xcode on **27.1**. That is what lets the app fill the iPhone Duo's
   inner screen and use the wide layout; an older Xcode makes it run there in
   a phone-sized window.
3. It takes roughly 30–50 minutes. Most of that is compiling the shared Kotlin
   for the iPhone the first time; later builds reuse a cache and are quicker.

When it finishes, the build goes to App Store Connect and takes a few more
minutes to process. Then it appears under your app → **TestFlight**.

---

## Part 8 — Get it onto your phone

1. Install **TestFlight** from the App Store on the iPhone.
2. In App Store Connect, open the app → **TestFlight** → **Internal Testing**.
3. Create a group (call it anything — "Me" will do) and add yourself as a
   tester with your Apple ID.
4. Tick the new build for that group.
5. You get an email; open TestFlight on the phone and install.

Internal testing goes to you alone and never to Apple's beta review, so there
is no waiting for approval and no questions to answer.

---

## When something goes wrong

| What you see | What it means |
| --- | --- |
| No **Run workflow** button on any of these workflows | They are not on `main` yet. GitHub only offers the button for workflows on the default branch. See the note in Part 6. |
| Red crosses on **iOS TestFlight build** lasting ~20 seconds | The secrets guard, working. Those runs fired on a push before the secrets existed and stopped rather than spending an hour to fail. |
| The TestFlight build stops in under a minute naming secrets | One or more of the four is missing. Part 5. |
| `Apple keys check` fails on "App Store Connect accepts the key" | The Issuer ID, Key ID and `.p8` do not belong to each other, or the key was revoked. Easiest fix is a fresh key: Part 3. |
| `Apple keys check` fails on "the app exists" | Part 2 was not finished, or the bundle ID does not match exactly. |
| The build fails at "Sign and upload" with a permissions error | The API key's access level is too low. Keys cannot be edited: revoke it, make a new **Admin** key, update `ASC_KEY_ID` and `ASC_KEY_P8`. |
| "No Xcode 27.1 on this image" | GitHub changed its Mac images. Re-run and choose 26.4.1 — you lose only the Duo's wide layout, not the app. |

If the iPhone code itself ever breaks, the **iPhone shared-code check**
workflow tells you in about ten minutes and needs none of the above.
