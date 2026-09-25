Opening sequence QA

Owner-supplied originals are preserved in incoming/01.jpg through 04.jpg and incoming/opening-song.mp3. The Android display copies in app/src/main/assets/splash/ are resized for phone memory. app/src/main/res/raw/opening_song.mp3 is byte-identical to the supplied audio (SHA-256 bab49fe103bc659ebdb625f76f77e74cd818c21ea0c619bb9e3d4b087dfa039d). The MP3's decoded duration is 6.624 seconds, and playback completion rather than a fixed timer should determine when the intro ends.

LaunchIntro is enabled in versionCode 12. First three frames use provisional arrival order at 0, 1.9 and 3.8 seconds. The owner explicitly designated fourth image for around the six-second mark, and it enters at 5.85 seconds to be visible at six seconds. These provisional timings are subject to his device feedback. Playback completion determines exit.
