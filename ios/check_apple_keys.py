"""Checks the Apple secrets the iOS build will use, without printing them.

The App Store Connect key (ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8) is used to
look the app up by its bundle ID, which needs a working key and an app record
created with that ID. APPLE_TEAM_ID is checked for shape only; the build uses
it to tell Apple which team's certificate to sign with.

This app sends no push notifications, so there is no push key to check - the
KU Volleyball app's copy of this script checks one because its match alerts
need it.

Only pass/fail lines and non-secret facts (the app's name, the App ID's
capabilities) are printed.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt

BUNDLE_ID = "io.github.inspectorgad.kcdiamonds"
ASC = "https://api.appstoreconnect.apple.com/v1"

results = []


def report(ok, what, detail=""):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {what}" + (f" - {detail}" if detail else ""))


def secret(name):
    value = os.environ.get(name, "").strip()
    report(bool(value), f"{name} is set", "" if value else "missing or empty")
    return value


def shape(name, value, pattern, hint):
    ok = bool(re.fullmatch(pattern, value))
    report(ok, f"{name} looks right", "" if ok else hint)
    return ok


def asc_get(token, path, params):
    url = f"{ASC}/{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.load(resp)
    except urllib.error.HTTPError as e:
        try:
            body = json.load(e)
        except Exception:
            body = {}
        return e.code, body


def asc_error(body):
    errors = body.get("errors") or [{}]
    return f"{errors[0].get('status', '')} {errors[0].get('code', '')}: {errors[0].get('detail', '')}".strip()


def check_signing_access(token):
    """Whether this key may sign, which is the half of the build that fails last.

    The upload step asks Apple to sign the archive with a distribution
    certificate it keeps for the team ("cloud signing"), and a key without
    enough access is refused - but only at the end of an hour-long build, with
    a message about provisioning rather than about the key. Listing
    certificates needs the same access, costs one request, and so moves that
    answer to the front. A Developer-role key gets 403 here; Admin and App
    Manager do not.
    """
    status, body = asc_get(token, "certificates", {"limit": "1"})
    if status == 200:
        report(True, "the key may sign builds", "it can read the team's certificates")
    elif status == 403:
        report(False, "the key may sign builds",
               "403 - this key's access level is too low to sign. A key's access cannot be "
               "changed after it is made: revoke it and generate a new one with Admin access")
    else:
        report(False, "the key may sign builds", f"HTTP {status} {asc_error(body)}")


def check_app_store_connect(issuer, key_id, p8):
    now = int(time.time())
    try:
        token = jwt.encode(
            {"iss": issuer, "iat": now, "exp": now + 600, "aud": "appstoreconnect-v1"},
            p8, algorithm="ES256", headers={"kid": key_id, "typ": "JWT"},
        )
    except Exception as e:
        report(False, "ASC_KEY_P8 is a usable private key", type(e).__name__)
        return
    report(True, "ASC_KEY_P8 is a usable private key")

    status, body = asc_get(token, "apps", {"filter[bundleId]": BUNDLE_ID})
    if status == 401:
        report(False, "App Store Connect accepts the key",
               "401 - the Issuer ID, Key ID and .p8 do not belong together, or the key was revoked")
        return
    if status != 200:
        report(False, "App Store Connect accepts the key", f"HTTP {status} {asc_error(body)}")
        return
    report(True, "App Store Connect accepts the key")
    apps = body.get("data", [])
    if apps:
        name = apps[0]["attributes"].get("name", "?")
        report(True, "the app exists in App Store Connect", f'"{name}"')
    else:
        report(False, "the app exists in App Store Connect",
               f"no app with bundle ID {BUNDLE_ID} - create it in App Store Connect first")

    status, body = asc_get(token, "bundleIds",
                           {"filter[identifier]": BUNDLE_ID, "include": "bundleIdCapabilities"})
    if status == 200 and body.get("data"):
        caps = sorted({c["attributes"].get("capabilityType", "?") for c in body.get("included", [])
                       if c.get("type") == "bundleIdCapabilities"})
        report(True, "the App ID is registered", ", ".join(caps) or "no capabilities listed")
    else:
        report(False, "the App ID is registered", f"HTTP {status} {asc_error(body)}")

    check_signing_access(token)


team = secret("APPLE_TEAM_ID")
issuer = secret("ASC_ISSUER_ID")
asc_key_id = secret("ASC_KEY_ID")
asc_p8 = secret("ASC_KEY_P8")

shape("APPLE_TEAM_ID", team, r"[A-Z0-9]{10}", "should be 10 capital letters and digits")
shape("ASC_ISSUER_ID", issuer, r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
      "should look like 69a6de7e-....-....-....-............ (the long one above the keys list)")
shape("ASC_KEY_ID", asc_key_id, r"[A-Z0-9]{10}", "should be 10 capital letters and digits")
report("BEGIN PRIVATE KEY" in asc_p8 and "END PRIVATE KEY" in asc_p8,
       "ASC_KEY_P8 is a whole .p8 file",
       "" if "BEGIN PRIVATE KEY" in asc_p8 else "paste the whole file, BEGIN and END lines included")

if issuer and asc_key_id and asc_p8:
    check_app_store_connect(issuer, asc_key_id, asc_p8)

passed = sum(results)
print(f"\n{passed} of {len(results)} checks passed")
sys.exit(0 if all(results) else 1)
