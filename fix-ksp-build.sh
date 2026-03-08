#!/bin/bash
echo "🔧 Fixing KSP/Hilt GitHub Actions build..."

mkdir -p .github/workflows
cat > .github/workflows/android-ci.yml << 'EOF'
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
        
    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v3
      
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Clean build
      run: ./gradlew clean --no-daemon
      
    - name: Build with Gradle
      run: ./gradlew assembleDebug --stacktrace --no-daemon
      
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: debug-apk
        path: app/build/outputs/apk/debug/*.apk
EOF

echo "✅ Created workflow"

if ! grep -q "ksp.incremental" gradle.properties 2>/dev/null; then
    cat >> gradle.properties << 'EOF'

# KSP Configuration - Critical for CI builds
ksp.incremental=false
ksp.useKSP2=false
ksp.allowSourcesFromOtherPlugins=true
org.gradle.jvmargs=-Xmx6g -Dfile.encoding=UTF-8 -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
EOF
    echo "✅ Added KSP config"
fi

git add .github/workflows/android-ci.yml gradle.properties
git commit -m "fix: KSP/Hilt GitHub Actions build configuration" || echo "Nothing to commit"
git push origin main

echo ""
echo "✅ Done! Check: https://github.com/tonygrischke-art/codemaster-ai-studio/actions"
