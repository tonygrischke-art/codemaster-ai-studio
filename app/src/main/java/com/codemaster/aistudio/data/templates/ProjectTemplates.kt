package com.codemaster.aistudio.data.templates

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val language: String,
    val icon: String,
    val files: Map<String, String> // relative path -> content
)

val PROJECT_TEMPLATES = listOf(
    ProjectTemplate(
        id = "kotlin_compose",
        name = "Kotlin + Compose",
        description = "Android app with Jetpack Compose, Material3, and Hilt DI",
        language = "Kotlin",
        icon = "🟣",
        files = mapOf(
            "app/src/main/java/com/example/app/MainActivity.kt" to """
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var count by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hello CodeMaster!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { count++ }) { Text("Tapped ${'$'}count times") }
    }
}
""".trimIndent(),
            "README.md" to "# My Kotlin Compose App

Built with CodeMaster AI Studio
"
        )
    ),
    ProjectTemplate(
        id = "python_cli",
        name = "Python CLI Tool",
        description = "Command-line tool with argparse and logging",
        language = "Python",
        icon = "🐍",
        files = mapOf(
            "main.py" to """
import argparse
import logging

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def main():
    parser = argparse.ArgumentParser(description='My CLI Tool')
    parser.add_argument('--name', default='World', help='Name to greet')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose output')
    args = parser.parse_args()

    if args.verbose:
        logger.setLevel(logging.DEBUG)

    logger.info(f'Hello, {args.name}!')
    print(f'Hello, {args.name}!')

if __name__ == '__main__':
    main()
""".trimIndent(),
            "requirements.txt" to "# Add your dependencies here
",
            "README.md" to "# Python CLI Tool

Built with CodeMaster AI Studio

## Usage
```
python main.py --name Tony
```
"
        )
    ),
    ProjectTemplate(
        id = "python_api",
        name = "Python REST API",
        description = "FastAPI REST API with endpoints and models",
        language = "Python",
        icon = "⚡",
        files = mapOf(
            "main.py" to """
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI(title="My API", version="1.0.0")

class Item(BaseModel):
    id: Optional[int] = None
    name: str
    description: Optional[str] = None

items: List[Item] = []

@app.get("/")
def root():
    return {"message": "API is running", "version": "1.0.0"}

@app.get("/items", response_model=List[Item])
def get_items():
    return items

@app.post("/items", response_model=Item)
def create_item(item: Item):
    item.id = len(items) + 1
    items.append(item)
    return item

@app.get("/items/{item_id}", response_model=Item)
def get_item(item_id: int):
    item = next((i for i in items if i.id == item_id), None)
    if not item:
        raise HTTPException(status_code=404, detail="Item not found")
    return item
""".trimIndent(),
            "requirements.txt" to "fastapi==0.109.0
uvicorn==0.27.0
pydantic==2.5.0
",
            "README.md" to "# FastAPI REST API

Built with CodeMaster AI Studio

## Run
```
pip install -r requirements.txt
uvicorn main:app --reload
```
"
        )
    ),
    ProjectTemplate(
        id = "empty",
        name = "Empty Project",
        description = "Start from scratch",
        language = "Kotlin",
        icon = "📄",
        files = mapOf(
            "README.md" to "# New Project

Created with CodeMaster AI Studio
"
        )
    )
)
