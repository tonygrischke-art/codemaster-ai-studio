import re

# GitViewModel.kt - hardcoded default path
path = "app/src/main/java/com/codemaster/aistudio/ui/screens/git/GitViewModel.kt"
with open(path, 'r') as f:
    content = f.read()
content = content.replace(
    '"/data/data/com.termux/files/home/codemaster-ai-studio"',
    'android.os.Environment.getExternalStorageDirectory().absolutePath + "/codemaster-ai-studio"'
)
with open(path, 'w') as f:
    f.write(content)
print("Fixed GitViewModel.kt")

# HomeScreen.kt - hardcoded default path
path = "app/src/main/java/com/codemaster/aistudio/ui/screens/home/HomeScreen.kt"
with open(path, 'r') as f:
    content = f.read()
content = content.replace(
    '"codemaster-ai-studio" to "/data/data/com.termux/files/home/codemaster-ai-studio"',
    '"codemaster-ai-studio" to (android.os.Environment.getExternalStorageDirectory().absolutePath + "/codemaster-ai-studio")'
)
with open(path, 'w') as f:
    f.write(content)
print("Fixed HomeScreen.kt")

# SettingsScreen.kt - placeholder
path = "app/src/main/java/com/codemaster/aistudio/ui/screens/settings/SettingsScreen.kt"
with open(path, 'r') as f:
    content = f.read()
content = content.replace(
    'placeholder = { Text("codemaster-ai-studio") }',
    'placeholder = { Text("e.g. /data/data/com.termux/files/home/myproject") }'
)
with open(path, 'w') as f:
    f.write(content)
print("Fixed SettingsScreen.kt")

print("All done!")
