import re

# Fix NavGraph.kt
with open("app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt", "r") as f:
    content = f.read()

content = re.sub(
    r'fun NavGraph\(navController: NavHostController.*?\)',
    'fun NavGraph(navController: NavHostController)',
    content
)

with open("app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt", "w") as f:
    f.write(content)
print("Fixed NavGraph.kt")

# Fix AiChatViewModel.kt - rewrite the broken sendMessage section
with open("app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatViewModel.kt", "r") as f:
    content = f.read()

# Replace the broken displayText line
content = re.sub(
    r'val displayText = .*?else text',
    'val displayText = if (attachedName != null) (text + "\\n" + attachedName) else text',
    content
)

with open("app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatViewModel.kt", "w") as f:
    f.write(content)
print("Fixed AiChatViewModel.kt")
