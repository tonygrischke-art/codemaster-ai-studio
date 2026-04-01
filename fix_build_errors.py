#!/usr/bin/env python3
"""
Fix 3 build errors in CodeMaster AI Studio.
Run from the repo root: python3 fix_build_errors.py
"""

import re

# ── FIX 1 ────────────────────────────────────────────────────────────────────
# FloatingNotepadService.kt:346
# `startY` is Offset (from detectTapGestures onPress), not Float.
# currentY is Float, so `currentY - startY` fails.
# Fix: use startY.y (the Float y-component of the Offset).

FNS = "app/src/main/java/com/codemaster/aistudio/ui/overlay/FloatingNotepadService.kt"

with open(FNS, "r") as f:
    src = f.read()

old = "                                    val startY = it\n"
new = "                                    val startY = it.y\n"

if old in src:
    src = src.replace(old, new, 1)
    with open(FNS, "w") as f:
        f.write(src)
    print("✅ Fix 1 applied: FloatingNotepadService.kt — startY = it.y")
else:
    print("⚠️  Fix 1 skipped (pattern not found — already fixed?)")

# ── FIX 2 ────────────────────────────────────────────────────────────────────
# HomeScreen.kt:255  Modifier.align() requires BoxScope.
# The Snackbar sits inside the Scaffold content lambda which was a Column.
# Fix: wrap the Scaffold content in Box { Column { ... } + dialogs + snackbar }

HS = "app/src/main/java/com/codemaster/aistudio/ui/screens/home/HomeScreen.kt"

with open(HS, "r") as f:
    src = f.read()

old_scaffold_open = (
    "    ) { padding ->\n"
    "        Column(modifier = Modifier.fillMaxSize().padding(padding)) {\n"
    "            // Top bar with recent projects, terminal/upload, and export buttons\n"
    "            TopActionBar("
)
new_scaffold_open = (
    "    ) { padding ->\n"
    "        Box(modifier = Modifier.fillMaxSize().padding(padding)) {\n"
    "        Column(modifier = Modifier.fillMaxSize()) {\n"
    "            // Top bar with recent projects, terminal/upload, and export buttons\n"
    "            TopActionBar("
)

if old_scaffold_open in src:
    src = src.replace(old_scaffold_open, new_scaffold_open, 1)
    print("✅ Fix 2a applied: HomeScreen.kt — Box wrapper opened")
else:
    print("⚠️  Fix 2a skipped (pattern not found — already fixed?)")

old_scaffold_close = (
    "            ) { Text(error) }\n"
    "        }\n"
    "    }\n"
    "}\n"
    "\n"
    "@Composable\n"
    "fun TopActionBar("
)
new_scaffold_close = (
    "            ) { Text(error) }\n"
    "        }\n"
    "        } // end Box\n"
    "    }\n"
    "}\n"
    "\n"
    "@Composable\n"
    "fun TopActionBar("
)

if old_scaffold_close in src:
    src = src.replace(old_scaffold_close, new_scaffold_close, 1)
    print("✅ Fix 2b applied: HomeScreen.kt — Box wrapper closed")
else:
    print("⚠️  Fix 2b skipped (pattern not found — already fixed?)")

# ── FIX 3 ────────────────────────────────────────────────────────────────────
# HomeScreen.kt:376,385,386,389
# MainCodeField uses ExposedDropdownMenuBox (ExperimentalMaterial3Api) but
# lacks @OptIn. HomeScreen has it, but MainCodeField is a separate function.

old_fn = "@Composable\nfun MainCodeField("
new_fn = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun MainCodeField("

if old_fn in src and "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun MainCodeField(" not in src:
    src = src.replace(old_fn, new_fn, 1)
    print("✅ Fix 3 applied: HomeScreen.kt — @OptIn added to MainCodeField")
else:
    print("⚠️  Fix 3 skipped (already has @OptIn or pattern not found)")

with open(HS, "w") as f:
    f.write(src)

print("\n✅ All fixes applied. Run: git add -A && git commit -m 'fix: resolve 3 Kotlin compile errors' && git push origin HEAD")
