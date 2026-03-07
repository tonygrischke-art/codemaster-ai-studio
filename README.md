# CodeMaster AI Studio

A powerful native Android IDE with dual AI assistant (Gemini + Kimi), built with Kotlin & Jetpack Compose.

## Features (Phase 1)
- ✅ AI Chat panel (Gemini 1.5 Flash + Moonshot Kimi)
- ✅ Dual provider toggle with conversation history
- ✅ Code block rendering with one-tap copy
- ✅ Quick prompt shortcuts (fix error, write code, explain, etc.)
- ✅ Room DB chat persistence per project
- ✅ DataStore settings (API keys, sandbox, auto-save)
- ✅ Material 3 dark/light theme
- 🔜 Code editor (Phase 2)
- 🔜 Terminal/console (Phase 2)
- 🔜 APK build tools (Phase 3)

## Setup (Termux / GitHub Actions)

### Add API Keys
1. Open the app → Settings
2. Paste your **Gemini API key** (from aistudio.google.com)
3. Paste your **Kimi API key** (from platform.moonshot.cn)

### Build from Termux
```bash
git clone https://github.com/YOUR_USERNAME/codemaster-ai-studio
cd codemaster-ai-studio
chmod +x gradlew
./gradlew assembleDebug
```

### Build via GitHub Actions
1. Push to GitHub
2. Go to **Settings → Secrets** and add:
   - `GEMINI_API_KEY`
   - `KIMI_API_KEY`
3. Push to `main` branch → Actions auto-builds APK
4. Download APK from **Actions → Artifacts**

## Architecture
- **Kotlin + Jetpack Compose** (Material 3)
- **Hilt** for dependency injection
- **Room** for chat/project persistence
- **Retrofit** for Gemini + Kimi API calls
- **DataStore** for settings
- **Navigation Compose** for screen routing

## Package
`com.codemaster.aistudio`
