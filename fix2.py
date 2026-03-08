import os
base = "/data/data/com.termux/files/home/CodeMasterAIStudio/app/src/main/java/com/codemaster/aistudio"

with open(f"{base}/data/repository/AiRepository.kt", "r") as f:
    ai = f.read()
ai = ai.replace("settingsRepository.getSettings()", "settingsRepository")
ai = ai.replace("settings.geminiApiKey", "settingsRepository.getGeminiApiKey()")
ai = ai.replace("settings.kimiApiKey", "settingsRepository.getKimiApiKey()")
ai = ai.replace("val settings = settingsRepository\n", "")
with open(f"{base}/data/repository/AiRepository.kt", "w") as f:
    f.write(ai)
print("1 done")

with open(f"{base}/di/AppModule.kt", "r") as f:
    mod = f.read()
mod = mod.replace("CodeMasterDatabase.getDatabase(context)", "androidx.room.Room.databaseBuilder(context, CodeMasterDatabase::class.java, CodeMasterDatabase.DATABASE_NAME).fallbackToDestructiveMigration().build()")
with open(f"{base}/di/AppModule.kt", "w") as f:
    f.write(mod)
print("2 done")

with open(f"{base}/ui/screens/terminal/TerminalScreen.kt", "r") as f:
    lines = f.readlines()
seen = set()
out = []
for line in lines:
    s = line.strip()
    if s.startswith("import "):
        if s in seen:
            continue
        seen.add(s)
    out.append(line)
with open(f"{base}/ui/screens/terminal/TerminalScreen.kt", "w") as f:
    f.writelines(out)
print("3 done")

with open(f"{base}/ui/screens/chat/AiChatScreen.kt", "r") as f:
    sc = f.read()
sc = sc.replace("@Composable\nfun AiChatScreen(", "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun AiChatScreen(")
with open(f"{base}/ui/screens/chat/AiChatScreen.kt", "w") as f:
    f.write(sc)
print("4 done")
