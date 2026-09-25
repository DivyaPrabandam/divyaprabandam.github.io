Opening sequence QA

Owner-supplied originals are preserved in incoming/01.jpg through 04.jpg and incoming/opening-song.mp3. The Android display copies in app/src/main/assets/splash/ are resized for phone memory. app/src/main/res/raw/opening_song.mp3 is byte-identical to the supplied audio (SHA-256 bab49fe103bc659ebdb625f76f77e74cd818c21ea0c619bb9e3d4b087dfa039d). The MP3's decoded duration is 6.624 seconds, and playback completion rather than a fixed timer should determine when the intro ends.

LaunchIntro is enabled in versionCode 13. Image 03 is excluded at the owner's request. Images 01 and 02 appear in arrival order at 0 and 2.9 seconds. The designated fourth image enters at 5.7 seconds and is fully blended by 6.0 seconds. Transitions are 300ms crossfades tied to the audio playback position. Playback completion determines exit; timing and visual feel remain subject to his device feedback.
