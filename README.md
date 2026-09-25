# Divya Prabandham Android - core reader alpha

Native Java/View Android project, minSdk 31 (Android 12), targetSdk 37. No account or external runtime library. The approval-pending branch now includes an app identifier and Internet permission for the user-initiated corrections feature; the pinned alpha remains offline and separate. Not yet the approved 42-screen v1; do not distribute as a finished edition.

Build with JDK 17 and Android SDK platform 37 / build tools 37:

    gradle :app:assembleDebug

Core milestone: Home, 25 prabandhams covering numbered pasurams 1-4000, offline book/index/reader and global search across the 4,000, Tamil and transliteration, four themes, persisted book/reading place and text size. The site's devotional text is bundled verbatim from live assets (see books/manifest.json with URLs and SHA-256 hashes); no text is generated. The two long madals are represented by one complete passage each, preserving their site's numbered ranges 2673-2712 and 2713-2790. Their internal split boundaries have not been invented.

No audio playback, meanings, maps or sourced devotional images are implemented yet. Other approved screens remain staged in the roadmap. Add cited meanings and tested audio in later milestones. Image/photo choices are still pending user review. Keep the same navigation and behaviour across themes. No auto-correction of devotional text.

Verification: Gradle debug build and 4,000-number asset checks pass. The user tested the preceding QA APK on Xiaomi 15 / Android 16: the four themes, index, reader, journey and correction entry were visible in a recording; Saved survived a force-close. This copy-only follow-up has not been visually checked on the phone. The local 2 GB host has no KVM, so API 12/17 runtime tests remain open. Corrections submission/retry still needs end-to-end verification against the live Worker.

## Local next-step branch (not in the committed alpha)

After the first pinned alpha was handed off, a separate, approval-pending branch added:
- Private manual read marks, a 25-book journey view, and saved pasurams. No streaks or leaderboard; the two long madals are one source passage each.
- Global search moved off the UI thread to a single worker, with 240 ms input debounce, an in-memory source-text index, and 20-result cap. It still needs real-device latency testing before a release claim.
- Reversible Focus control and an elder display mode (larger type/controls). Both need real-device visual QA in all four themes.

The first version in the repo and the current local workspace may differ. Confirm a repository commit before offering an APK link, and confirm the device screenshot is from that same commit.

This branch is isolated from the pinned API 29 alpha. The 2026 Android 17 SDK setup guide specifies API 37: https://developer.android.com/about/versions/17/setup-sdk. Setting compile/target 37 does not prove runtime compatibility without a device test. MinSdk 31 excludes Android 10/11 that the earlier five-year buffer included; This tradeoff was surfaced at the morning review.

## Approval-pending corrections

The reader's correction sheet sends only after a user taps Submit, then saves a private atomic on-device queue before trying the HTTPS endpoint. Image attachments are resized locally to at most 1600 px and must be under 1.5 MB. User-supplied source/credit is required for attached images. A network-gated persisted JobScheduler job retries queued 429, 5xx and network failures. 400/413 replies are held for review with the server's error. A response is removed only on HTTP 200 with `{"ok":true}`. The current verse's original Tamil and transliteration are sent for text diffs. Nothing automatically edits the devotional text. Submission semantics and presentation need live device and staging-server QA before release; no test submission was sent by this development task.

## Content updater branch (fixture-only until publisher contract)

VersionCode 4 adds a native SHA256withECDSA/P-256 manifest verifier, private atomic book snapshots, full 1-4000 number/hash/size checks, 8-hour network-gated WorkManager cadence, automatic launch checks, a Wi-Fi/full vs mobile/light image policy and settings, and a visible incompatible-app state. The public key, manifest URL, image associations and APK installer route remain unconfigured. This build does not fetch remote content or submit corrections; do not distribute it as a working updater. It requires a signed canonical publisher manifest, local fixture tests, phone QA and a verified app-key corrections path before release.
