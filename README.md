# Divya Prabandham Android - core reader alpha

Native Java/View Android project, minSdk 31 (Android 12), targetSdk 37. No account, key, network permission or external runtime library. Not yet the approved 42-screen v1; do not distribute as a finished edition.

Build with JDK 17 and Android SDK platform 37 / build tools 37:

    gradle :app:assembleDebug

Core milestone: Home, 25 prabandhams covering numbered pasurams 1-4000, offline book/index/reader and global search across the 4,000, Tamil and transliteration, four themes, persisted book/reading place and text size. The site's devotional text is bundled verbatim from live assets (see books/manifest.json with URLs and SHA-256 hashes); no text is generated. The two long madals are represented by one complete passage each, preserving their site's numbered ranges 2673-2712 and 2713-2790. Their internal split boundaries have not been invented.

No audio, meanings, maps or images are implemented yet. Other approved screens remain staged in the roadmap. Add cited meanings and tested audio in later milestones. Image/photo choices are still pending user review. Keep the same navigation and behaviour across themes. No auto-correction of devotional text.

Verification so far: Gradle debug build passes, manifest identifies SDK floor; manifest validation finds all 4,000 numbers once with no gaps. Real Android UI screenshot and interactive QA are still required before an installable alpha can be offered for use. A software API 29 emulator did start but its full boot/Package Manager did not become responsive under this host's 2 GB RAM and no KVM; Android 12 and 17 interactive QA remains open.

## Local next-step branch (not in the committed alpha)

After the first pinned alpha was handed off, a separate, approval-pending branch added:
- Private manual read marks and a 25-book journey view. No streaks or leaderboard; the two long madals are one source passage each.
- Global search moved off the UI thread to a single worker, with 240 ms input debounce and 20-result cap. It still needs real-device latency testing before a release claim.

The first version in the repo and the current local workspace may differ. Confirm a repository commit before offering an APK link, and confirm the device screenshot is from that same commit.

This branch is isolated from the pinned API 29 alpha. The 2026 Android 17 SDK setup guide specifies API 37: https://developer.android.com/about/versions/17/setup-sdk. Setting compile/target 37 does not prove runtime compatibility without a device test. MinSdk 31 excludes Android 10/11 that the earlier five-year buffer included; owner to confirm at the morning review.
