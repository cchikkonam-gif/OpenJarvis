# DANGER AI — Android client for OpenJarvis

An independent native Android client; the upstream OpenJarvis Python engine is unchanged.
Supports Android 8+ (tested build target API 35), an OpenAI-compatible chat endpoint, persistent local conversation history, system speech input and text-to-speech, and UTF-8 text document import.

## Connect
On a trusted local network, install OpenJarvis and run `jarvis serve --host 0.0.0.0 --port 8000` on your server. Enter its private LAN URL (for example http://192.168.1.10:8000) and actual model ID under Settings. For remote access, use a secured HTTPS reverse proxy and API key. Do not expose an unauthenticated agent server to the public Internet. The API key is held in memory only. Chat history is stored locally in app-private storage; text is sent to the configured endpoint when Send is pressed.

An externally running local inference server on the phone may also be connected at localhost. This APK does not bundle a language model, Python runtime, on-device inference engine, image/PDF extraction, or social-media automation. Speech recognition depends on the installed Android service and may use the network.

## Build
In GitHub Actions, enable workflows for the fork if required, then run DANGER AI Android APK. Download the artifact and extract DANGER-AI.apk. It is debug-signed for testing, not a production release.
Local build: JDK 17, Android SDK platform 35, Gradle 8.11.1; run `gradle -p android-mobile assembleDebug`.
Upstream license: Apache 2.0 (see repository LICENSE).
