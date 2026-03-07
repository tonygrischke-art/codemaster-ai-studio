#!/bin/bash
# CodeMaster AI Studio - Git Repo Setup Script
# Run this from inside your CodeMasterAIStudio project folder

set -e

echo "=== CodeMaster AI Studio - Git Setup ==="
echo ""

# 1. Check if already a git repo
if [ -d ".git" ]; then
  echo "⚠️  .git already exists, skipping init"
else
  git init
  echo "✅ Git initialized"
fi

# 2. Set your identity (edit these if not already configured)
# git config user.name "Your Name"
# git config user.email "you@example.com"

# 3. Create initial commit
git add .
git commit -m "feat: initial CodeMaster AI Studio scaffold

- AI Chat panel with Gemini + Kimi dual provider
- Room DB for chat/project persistence
- DataStore settings (API keys, sandbox, auto-save)
- Hilt DI + Retrofit for both AI APIs
- Material 3 dark theme
- Navigation Compose with 5 screens
- GitHub Actions CI for APK builds"

echo "✅ Initial commit created"
echo ""

# 4. Prompt for GitHub repo URL
echo "Now create a NEW repo on GitHub:"
echo "  → github.com/new"
echo "  → Name: codemaster-ai-studio"
echo "  → Private or Public (your choice)"
echo "  → Do NOT add README, .gitignore, or license (we have them)"
echo ""
read -p "Paste your GitHub repo URL (e.g. https://github.com/YourUser/codemaster-ai-studio.git): " REPO_URL

if [ -z "$REPO_URL" ]; then
  echo "❌ No URL entered. Run this manually:"
  echo "   git remote add origin YOUR_REPO_URL"
  echo "   git branch -M main"
  echo "   git push -u origin main"
  exit 1
fi

git remote add origin "$REPO_URL"
git branch -M main
git push -u origin main

echo ""
echo "✅ Pushed to GitHub!"
echo ""
echo "=== Next steps ==="
echo "1. Go to your repo → Settings → Secrets and variables → Actions"
echo "2. Add secret: GEMINI_API_KEY  (from aistudio.google.com)"
echo "3. Add secret: KIMI_API_KEY    (from platform.moonshot.cn)"
echo "4. GitHub Actions will auto-build your APK on every push to main"
echo ""
echo "Download APK: repo → Actions tab → latest run → Artifacts"
