with open('app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatScreen.kt', 'r') as f:
    content = f.read()

# Find the start of the duplicate ViewModel class
marker = '\n@HiltViewModel\nclass AiChatViewModel'
idx = content.find(marker)
if idx == -1:
    print("Not found!")
else:
    # Trim everything from that point onward
    content = content[:idx]
    with open('app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatScreen.kt', 'w') as f:
        f.write(content)
    print(f"Removed duplicate at char {idx}")
    print("Last 3 lines:", content.strip().splitlines()[-3:])
