#!/usr/bin/env python3
import os

path = os.path.expanduser(
    "~/codemaster-ai-studio/app/src/main/java/com/codemaster/aistudio/ui/screens/terminal/TerminalViewModel.kt"
)

with open(path, 'r') as f:
    content = f.read()

# Fix stdout reader - replace the problematic pattern
content = content.replace(
    '''                        try {
                            var line: String?
                            while (isActive) {
                                line = stdout.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (_: Exception) {}''',
    '''                        try {
                            while (isActive) {
                                val line = stdout.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (_: Exception) {}'''
)

# Fix stderr reader
content = content.replace(
    '''                        try {
                            var line: String?
                            while (isActive) {
                                line = stderr.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (_: Exception) {}''',
    '''                        try {
                            while (isActive) {
                                val line = stderr.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (_: Exception) {}'''
)

with open(path, 'w') as f:
    f.write(content)

print("✅ Fixed TerminalViewModel.kt smart cast error")
print("\nNow run:")
print("  git add -A")
print("  git commit -m 'fix: smart cast error in TerminalViewModel'")
print("  git push origin HEAD")
