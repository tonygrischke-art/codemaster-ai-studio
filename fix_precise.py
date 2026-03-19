# Fix NavGraph line 28 - duplicated route
nav_path = "app/src/main/java/com/codemaster/aistudio/ui/navigation/NavGraph.kt"
with open(nav_path) as f:
    content = f.read()

content = content.replace(
    '    object Preview  : Screen("preview/{projectId}") { fun createRoute(id: Long = -1L) = "preview/$id" }      { fun createRoute(id: Long = -1L) = "git/$id" }',
    '    object Preview  : Screen("preview/{projectId}") { fun createRoute(id: Long = -1L) = "preview/$id" }'
)
with open(nav_path, 'w') as f:
    f.write(content)
print("Fixed NavGraph.kt")

# Fix AiChatViewModel - multiline string broke across lines
vm_path = "app/src/main/java/com/codemaster/aistudio/ui/screens/chat/AiChatViewModel.kt"
with open(vm_path) as f:
    content = f.read()

content = content.replace(
    'val displayText = if (attachedName != null) "$text\n $attachedName" else text',
    'val displayText = if (attachedName != null) (text + "\\n" + (attachedName ?: "")) else text'
)
content = content.replace(
    'val projectContextSection = state.projectContext?.let { "\n\nProject file tree:\n$it" } ?: ""',
    'val projectContextSection = state.projectContext?.let { "\\n\\nProject file tree:\\n$it" } ?: ""'
)
with open(vm_path, 'w') as f:
    f.write(content)
print("Fixed AiChatViewModel.kt")
