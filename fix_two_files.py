import os

# Read and show line 28 of NavGraph
nav = open("app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt").readlines()
print("NavGraph line 28:", repr(nav[27]))

# Read and show lines 232-240 of AiChatViewModel
vm = open("app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatViewModel.kt").readlines()
print("ViewModel lines 232-240:")
for i, line in enumerate(vm[231:240], 232):
    print(f"  {i}: {repr(line)}")
