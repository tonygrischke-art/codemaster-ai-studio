#!/usr/bin/env python3
"""
Fix the infinite "Select Project Directory" loop in CodeMaster AI Studio.

Root causes:
  1. MainActivity: `hasAccess` is a plain val captured ONCE at onCreate start.
     After recreate(), it re-evaluates — but the real check (SafHelper) is broken.
  2. SafHelper.hasPersistedAccess(): calls openInputStream() on a TREE URI.
     Tree URIs are never directly openable as streams → always throws → always false.
     The correct check is: does this URI exist in contentResolver.persistedUriPermissions?
  3. SafHelper: buildDocumentUriUsingTree() called with nullable Uri? instead of String treeDocId.
  4. MainActivity: `hasAccess` must be reactive (remember/mutableStateOf) so the UI
     updates without needing recreate() at all.

Run from repo root: python3 fix_saf_loop.py
"""

SAFE_HELPER = "app/src/main/java/com/codemaster/aistudio/data/util/SafHelper.kt"
MAIN_ACTIVITY = "app/src/main/java/com/codemaster/aistudio/MainActivity.kt"

# ─────────────────────────────────────────────────────────────────────────────
# FIX 1 — SafHelper.hasPersistedAccess()
# Replace the broken openInputStream() check with the correct
# persistedUriPermissions check (the only reliable way to verify a tree URI grant).
# ─────────────────────────────────────────────────────────────────────────────
with open(SAFE_HELPER, "r") as f:
    saf = f.read()

OLD_HAS_ACCESS = '''    fun hasPersistedAccess(): Boolean {
        return persistedUri?.let { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.close()
                true
            } catch (e: Exception) {
                Log.w(TAG, "Persisted URI no longer valid: $uri")
                false
            }
        } ?: false
    }'''

NEW_HAS_ACCESS = '''    fun hasPersistedAccess(): Boolean {
        val uri = persistedUri ?: return false
        // Tree URIs cannot be opened as streams - check the persisted grants table instead
        val granted = context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri &&
            permission.isReadPermission &&
            permission.isWritePermission
        }
        if (!granted) {
            Log.w(TAG, "No persisted permission found for URI: $uri — clearing saved URI")
            persistedUri = null
        }
        return granted
    }'''

if OLD_HAS_ACCESS in saf:
    saf = saf.replace(OLD_HAS_ACCESS, NEW_HAS_ACCESS, 1)
    print("✅ Fix 1 applied: SafHelper.hasPersistedAccess() — proper persistedUriPermissions check")
else:
    print("⚠️  Fix 1 skipped (pattern not found — already fixed?)")

# ─────────────────────────────────────────────────────────────────────────────
# FIX 2 — SafHelper.takePersistablePermission()
# After taking the permission, verify it actually landed in persistedUriPermissions.
# If it didn't (e.g. the system rejected it), clear the stored URI so we don't
# silently pretend we have access.
# ─────────────────────────────────────────────────────────────────────────────

OLD_TAKE_PERM = '''    fun takePersistablePermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission for $uri", e)
        }
    }'''

NEW_TAKE_PERM = '''    fun takePersistablePermission(uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            // Verify the grant actually landed
            val confirmed = context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == uri && perm.isReadPermission && perm.isWritePermission
            }
            if (!confirmed) {
                Log.e(TAG, "takePersistableUriPermission did not result in a persisted grant for $uri")
            }
            confirmed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable permission for $uri", e)
            false
        }
    }'''

if OLD_TAKE_PERM in saf:
    saf = saf.replace(OLD_TAKE_PERM, NEW_TAKE_PERM, 1)
    print("✅ Fix 2 applied: SafHelper.takePersistablePermission() — verified grant")
else:
    print("⚠️  Fix 2 skipped (pattern not found — already fixed?)")

# ─────────────────────────────────────────────────────────────────────────────
# FIX 3 — SafHelper.onDirectoryPicked()
# If takePersistablePermission() fails, don't persist the URI.
# ─────────────────────────────────────────────────────────────────────────────

OLD_ON_PICKED = '''    fun onDirectoryPicked(uri: Uri) {
        persistedUri = uri
        takePersistablePermission(uri)
    }'''

NEW_ON_PICKED = '''    fun onDirectoryPicked(uri: Uri): Boolean {
        // Take the permission FIRST — only store URI if the grant was confirmed
        persistedUri = uri  // temporarily store so hasPersistedAccess() can validate
        val granted = takePersistablePermission(uri)
        if (!granted) {
            Log.e(TAG, "Permission not granted for $uri — not persisting URI")
            persistedUri = null
        }
        return granted
    }'''

if OLD_ON_PICKED in saf:
    saf = saf.replace(OLD_ON_PICKED, NEW_ON_PICKED, 1)
    print("✅ Fix 3 applied: SafHelper.onDirectoryPicked() — only persists if grant confirmed")
else:
    print("⚠️  Fix 3 skipped (pattern not found — already fixed?)")

# ─────────────────────────────────────────────────────────────────────────────
# FIX 4 — SafHelper.readFile/writeFile/deleteFile: persistedUri? (nullable) passed
# directly to buildDocumentUriUsingTree which expects a non-null Uri tree URI.
# Add null-guards so these ops fail cleanly instead of NPE-ing.
# ─────────────────────────────────────────────────────────────────────────────

OLD_READ = '''    suspend fun readFile(docId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(persistedUri, docId)'''

NEW_READ = '''    suspend fun readFile(docId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)'''

if OLD_READ in saf:
    saf = saf.replace(OLD_READ, NEW_READ, 1)
    print("✅ Fix 4a applied: SafHelper.readFile() — null guard on persistedUri")
else:
    print("⚠️  Fix 4a skipped (pattern not found — already fixed?)")

OLD_WRITE = '''    suspend fun writeFile(docId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(persistedUri, docId)'''

NEW_WRITE = '''    suspend fun writeFile(docId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)'''

if OLD_WRITE in saf:
    saf = saf.replace(OLD_WRITE, NEW_WRITE, 1)
    print("✅ Fix 4b applied: SafHelper.writeFile() — null guard on persistedUri")
else:
    print("⚠️  Fix 4b skipped (pattern not found — already fixed?)")

OLD_DELETE = '''    suspend fun deleteFile(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val uri = DocumentsContract.buildDocumentUriUsingTree(persistedUri, docId)'''

NEW_DELETE = '''    suspend fun deleteFile(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rootUri = persistedUri ?: return@withContext Result.failure(Exception("No directory selected"))
            val uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)'''

if OLD_DELETE in saf:
    saf = saf.replace(OLD_DELETE, NEW_DELETE, 1)
    print("✅ Fix 4c applied: SafHelper.deleteFile() — null guard on persistedUri")
else:
    print("⚠️  Fix 4c skipped (pattern not found — already fixed?)")

with open(SAFE_HELPER, "w") as f:
    f.write(saf)

# ─────────────────────────────────────────────────────────────────────────────
# FIX 5 — MainActivity: make `hasAccess` reactive with mutableStateOf.
# The plain `val hasAccess = safHelper.hasPersistedAccess()` is evaluated once
# at the start of onCreate. After the user picks a directory and recreate() runs,
# it re-evaluates — but only because of recreate(). With the reactive approach
# we can drop recreate() entirely and update the UI immediately.
# ─────────────────────────────────────────────────────────────────────────────
with open(MAIN_ACTIVITY, "r") as f:
    main = f.read()

OLD_LAUNCHER = '''    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                safHelper.onDirectoryPicked(uri)
                recreate()
            }
        }
    }'''

NEW_LAUNCHER = '''    // Reactive flag — updated immediately after directory pick, no recreate() needed
    private var hasDirectoryAccess = mutableStateOf(false)

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val granted = safHelper.onDirectoryPicked(uri)
                if (granted) {
                    hasDirectoryAccess.value = true
                } else {
                    android.widget.Toast.makeText(
                        this,
                        "Directory permission was not granted. Please try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }'''

if OLD_LAUNCHER in main:
    main = main.replace(OLD_LAUNCHER, NEW_LAUNCHER, 1)
    print("✅ Fix 5a applied: MainActivity — reactive hasDirectoryAccess, no recreate()")
else:
    print("⚠️  Fix 5a skipped (pattern not found — already fixed?)")

OLD_ONCREATE = '''        val hasAccess = safHelper.hasPersistedAccess()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()

            CodeMasterTheme(darkTheme = settingsState.isDarkTheme) {
                if (hasAccess) {'''

NEW_ONCREATE = '''        // Initialise the reactive flag from persisted state
        hasDirectoryAccess.value = safHelper.hasPersistedAccess()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            val hasAccess by hasDirectoryAccess

            CodeMasterTheme(darkTheme = settingsState.isDarkTheme) {
                if (hasAccess) {'''

if OLD_ONCREATE in main:
    main = main.replace(OLD_ONCREATE, NEW_ONCREATE, 1)
    print("✅ Fix 5b applied: MainActivity — setContent uses reactive hasAccess via `by`")
else:
    print("⚠️  Fix 5b skipped (pattern not found — already fixed?)")

# Add missing import for mutableStateOf / androidx.compose.runtime
OLD_IMPORTS_ANCHOR = "import android.content.Intent\n"
NEW_IMPORTS_ANCHOR = "import android.content.Intent\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.getValue\n"

if NEW_IMPORTS_ANCHOR not in main:
    main = main.replace(OLD_IMPORTS_ANCHOR, NEW_IMPORTS_ANCHOR, 1)
    print("✅ Fix 5c applied: MainActivity — added mutableStateOf/getValue imports")
else:
    print("⚠️  Fix 5c skipped (imports already present)")

with open(MAIN_ACTIVITY, "w") as f:
    f.write(main)

print("""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ All SAF loop fixes applied.

Summary of what was broken and what was fixed:

1. SafHelper.hasPersistedAccess() — used openInputStream() on a tree URI
   (always throws, always returned false). Now checks persistedUriPermissions.

2. SafHelper.takePersistablePermission() — never verified the grant stuck.
   Now returns Boolean and confirms via persistedUriPermissions.

3. SafHelper.onDirectoryPicked() — always stored the URI regardless of whether
   the permission grant succeeded. Now only persists if confirmed.

4. SafHelper read/write/delete — passed nullable persistedUri? directly to
   buildDocumentUriUsingTree (potential NPE). Now null-guarded.

5. MainActivity — hasAccess was a plain val evaluated once; UI never updated
   reactively. Now uses mutableStateOf, updates immediately, no recreate() needed.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Run:
  git add -A && git commit -m "fix: resolve infinite SAF directory picker loop" && git push origin HEAD
""")
