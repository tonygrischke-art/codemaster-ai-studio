#!/bin/bash
set -e
cd ~/CodeMasterAIStudio

echo "1. Fixing gradle.properties..."
grep -q "ksp.incremental=false" gradle.properties || echo "ksp.incremental=false" >> gradle.properties
grep -q "ksp.useKSP2=false" gradle.properties || echo "ksp.useKSP2=false" >> gradle.properties

echo "2. Fixing CI workflow..."
cat > .github/workflows/android-ci.yml << 'YAML'
name: Android CI
on:
  push:
    branches: [ main ]
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
    - uses: actions/checkout@v4
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    - name: Grant execute permission
      run: chmod +x gradlew
    - name: Clean
      run: ./gradlew clean --no-daemon
    - name: Build
      run: ./gradlew assembleDebug --no-daemon --stacktrace
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: debug-apk
        path: app/build/outputs/apk/debug/*.apk
YAML

echo "3. Pushing to GitHub..."
git add -A
git commit -m "fix: KSP incremental off + clean build in CI"
git push origin main

echo ""
echo "✅ Done! Check: https://github.com/tonygrischke-art/codemaster-ai-studio/actions"
