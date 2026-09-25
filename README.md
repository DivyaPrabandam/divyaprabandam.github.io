# Divya Prabandham Android - v7 production candidate

Native Java/View Android reader, Android 12+ (minSdk 31), target/compile 37. This local branch is separate from the previously installed QA APKs and has no public release asset yet.

## Included and grounded

The APK bundles site-source text for 25 prabandhams with exact numbered coverage 1-4000, preserving each long madal as one unsplit source passage. Home/resume, book/index/reader, Tamil/transliteration, previous/next, four themes including AMOLED, text-size controls, elder mode, reversible Focus, private saved pasurams and manual read marks with a 25-book journey, and debounced offline global search are implemented. Text integrity: `python3 scripts/check_assets.py "$PWD"` verifies all 25 asset hashes, bytes, unique numbered coverage and nonempty text.

Content updater code validates P-256 signed manifests and per-shard hashes, downloads only changed books, stages an atomic private snapshot, keeps the APK's offline corpus as fallback, and uses an 8-hour connected WorkManager cadence. The production P-256 public verification key is pinned, but the production endpoint is intentionally unset until deployment and live verification. Production snapshots are isolated from the old synthetic QA storage. The app cannot claim live content updates yet. Image policy scaffolding allows full images on Wi-Fi/light images on mobile with bounded cache, but real devotional images are not yet associated or displayed.

Corrections submission is hard-disabled pending the rotated app-key injection, current Worker contract, and an actual phone-path test. Correction-mode drafts are private per-pasuram atomic files; the sheet/review queue code does not send and is not a functioning correction service in this build. Draft retention has not had device QA. Do not reactivate it by changing only the boolean.

The launcher currently includes two icon styles, a rounded-corner painting and an in-app selectable cutout of the figures. The cutout preview was rejected by the owner; replacement owner-supplied transparent artwork is pending. Do not ship the current cutout as an approved icon. Alias switching has not been device-tested.

No audio playback or meanings are packaged. The two long madals have no source recitation URLs and audio licensing/rights are not resolved. The agreed later roadmap includes search upgrades, per-book resume, daily notification, Margazhi mode, notes/highlights, opt-in temple visit companion, recitation-session mode and offline audio. These are not part of this v7 reading candidate. Learn/Explore remain placeholders.

## Release gate

Build with JDK 17 and SDK 37: `ANDROID_HOME=/tmp/android-sdk JAVA_HOME=/tmp/jdk17 /tmp/gradle-root/gradle-8.13/bin/gradle :app:assembleDebug`. No direct production download link exists. Before one is created: configure the staged signed production publisher and verify live current text, replace the rejected cutout icon with the owner's supplied image and check it on-device, complete corrections phone-path QA if enabling it, run real-device functional/security/battery/RAM checks on Android 12+ including the Xiaomi 15 Android 16 phone, and visually inspect all four themes. Host software emulator could not boot due no KVM, 2 GB RAM and disk requirements. The v6 phone QA confirmed the earlier book-list crash was fixed, not this new binary. Do not merge/release merely because compilation passes.
