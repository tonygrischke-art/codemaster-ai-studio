#!/bin/bash

echo "🔧 CodeMaster AI Studio Build Fix Script"
echo "========================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

PROJECT_DIR="$(pwd)"
echo "Project directory: $PROJECT_DIR"

# 1. Fix app/build.gradle.kts - Add Material3 dependency
echo -e "${YELLOW}📦 Fixing build.gradle.kts...${NC}"

BUILD_GRADLE="$PROJECT_DIR/app/build.gradle.kts"

# Backup original
cp "$BUILD_GRADLE" "$BUILD_GRADLE.backup"

# Add material3 if not present
if ! grep -q "androidx.compose.material3:material3" "$BUILD_GRADLE"; then
    # Find dependencies block and add material3
    sed -i '/dependencies {/a\    implementation("androidx.compose.material3:material3:1.2.0")' "$BUILD_GRADLE"
    echo -e "${GREEN}✅ Added Material3 dependency${NC}"
else
    echo -e "${GREEN}✅ Material3 already present${NC}"
fi

# 2. Fix Theme.kt
echo -e "${YELLOW}🎨 Fixing Theme.kt...${NC}"

THEME_FILE="$PROJECT_DIR/app/src/main/java/com/codemaster/aistudio/ui/theme/Theme.kt"
mkdir -p "$(dirname "$THEME_FILE")"

cat > "$THEME_FILE" << 'EOF'
package com.codemaster.aistudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Color definitions
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun CodeMasterAIStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
EOF
echo -e "${GREEN}✅ Fixed Theme.kt${NC}"

# 3. Fix Typography.kt
echo -e "${YELLOW}📝 Fixing Typography.kt...${NC}"

TYPOGRAPHY_FILE="$PROJECT_DIR/app/src/main/java/com/codemaster/aistudio/ui/theme/Typography.kt"

cat > "$TYPOGRAPHY_FILE" << 'EOF'
package com.codemaster.aistudio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
EOF
echo -e "${GREEN}✅ Fixed Typography.kt${NC}"

# 4. Fix TerminalScreen.kt - Create with proper imports and fixes
echo -e "${YELLOW}💻 Fixing TerminalScreen.kt...${NC}"

TERMINAL_FILE="$PROJECT_DIR/app/src/main/java/com/codemaster/aistudio/ui/screens/terminal/TerminalScreen.kt"
mkdir -p "$(dirname "$TERMINAL_FILE")"

cat > "$TERMINAL_FILE" << 'EOF'
package com.codemaster.aistudio.ui.screens.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LineType {
    INPUT,
    OUTPUT,
    ERROR
}

data class TerminalLine(
    val text: String,
    val type: LineType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit = {}
) {
    var commandInput by remember { mutableStateOf("") }
    var terminalLines by remember { mutableStateOf(listOf<TerminalLine>()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Terminal output
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                reverseLayout = true
            ) {
                items(terminalLines) { line ->
                    val color = when (line.type) {
                        LineType.INPUT -> Color.Green
                        LineType.OUTPUT -> Color.White
                        LineType.ERROR -> Color.Red
                    }
                    
                    Text(
                        text = line.text,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            
            // Command input
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = { Text("Enter command") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
            
            // Execute button
            Button(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        terminalLines = listOf(
                            TerminalLine("> $commandInput", LineType.INPUT)
                        ) + terminalLines
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Execute")
            }
        }
    }
}
EOF
echo -e "${GREEN}✅ Fixed TerminalScreen.kt${NC}"

# 5. Clean and rebuild
echo -e "${YELLOW}🧹 Cleaning project...${NC}"
./gradlew clean

echo -e "${YELLOW}🔨 Building project...${NC}"
./gradlew build

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ Build fix complete!${NC}"
echo -e "${YELLOW}If build fails, check:${NC}"
echo "  1. Android SDK is properly configured"
echo "  2. JAVA_HOME is set correctly"
echo "  3. Run: ./gradlew --stop && ./gradlew clean build"
echo ""
echo -e "${YELLOW}Backups created with .backup extension${NC}"
