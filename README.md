# Surround Split Player (Android TV)

Plays a 5.1 file and splits the audio between two physical outputs:
* FRONT (FL, FR, + centre + LFE folded in) -> TV speakers / HDMI
* REAR  (BL, BR)                           -> AUX (3.5mm / USB DAC / Bluetooth)

## Build
1. Open this folder in Android Studio (Koala or newer), let Gradle sync.
2. (Strongly recommended) enable the FFmpeg decoders - see app/build.gradle.kts and
   SplitRenderersFactory.kt. Without them, AC3/EAC3/DTS files only work if the TV has a platform decoder.
3. Run on the TV / box (adb connect <ip>), or build an APK and sideload.

## Use
Pick a video, choose FRONT and REAR outputs, press PLAY. A toast shows the decoded format/channel count
(you should see 6 ch). During playback: CH+/CH- (or PgUp/PgDn) adjust sync in 10 ms steps.
Positive = delay rear, negative = delay front (Bluetooth rear usually needs a negative value, ~ -150..-300).

## How it works
ExoPlayer decodes to 6-ch PCM (passthrough is disabled). SplitAudioSink feeds the same PCM to two
DefaultAudioSinks, each with a ChannelRouteProcessor (front mix / rear pair + delay line) and its own
AudioTrack pinned to a device with setPreferredDevice().
