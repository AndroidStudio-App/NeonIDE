package com.neonide.studio

import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Node
import cafe.adriel.bonsai.filesystem.FileSystemBonsaiStyle
import cafe.adriel.bonsai.filesystem.FileSystemTree
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

@Composable
fun FileTreeDrawer(
    rootPath: String,
    onFileClick: (String) -> Unit
) {
    if (rootPath.isBlank()) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val rootPathState = remember(rootPath) { rootPath.toPath() }
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // --- Zoom State ---
    var uiScale by remember { mutableStateOf(1f) }
    
    // Create a scaled style
    val scaledStyle = remember(uiScale) {
        val base = FileSystemBonsaiStyle()
        base.copy(
            toggleIconSize = 16.dp * uiScale,
            nodeIconSize = 24.dp * uiScale,
            nodeNameStartPadding = 4.dp * uiScale,
            nodePadding = PaddingValues(
                horizontal = 8.dp * uiScale, // Slightly more horizontal padding for easier clicking
                vertical = 2.dp * uiScale
            ),
            nodeNameTextStyle = base.nodeNameTextStyle.copy(
                fontSize = 12.sp * uiScale
            ),
            // Disable internal horizontal scroll to prevent it from swallowing pinch gestures
            useHorizontalScroll = false 
        )
    }

    val tree = FileSystemTree(
        rootPath = rootPathState,
        fileSystem = FileSystem.SYSTEM,
        selfInclude = true,
        refreshTrigger = refreshTrigger
    )

    var nodeToAct by remember { mutableStateOf<Node<okio.Path>?>(null) }
    var actionDialog by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    val dirLastModified = remember(rootPath) { mutableMapOf<String, Long>() }
    val dirSnapshots = remember(rootPath) { mutableMapOf<String, String>() }

    LaunchedEffect(tree) {
        tree.expandRoot()
    }

    // Polling-based auto-refresh
    LaunchedEffect(rootPath, tree) {
        while (true) {
            delay(1500)
            val isDirty = withContext(Dispatchers.IO) {
                var dirty = false
                val currentNodes = tree.nodes.toList()
                for (node in currentNodes) {
                    if (node is BranchNode<*> && node.isExpanded) {
                        val file = File(node.content.toString())
                        if (file.exists() && file.isDirectory) {
                            val lastMod = file.lastModified()
                            val cachedMod = dirLastModified[file.absolutePath] ?: 0L
                            if (lastMod != cachedMod) {
                                val files = file.listFiles()
                                val snapshot = files?.sortedBy { it.name }?.joinToString { 
                                    "${it.name}:${it.isDirectory}" 
                                } ?: ""
                                val cachedSnapshot = dirSnapshots[file.absolutePath] ?: ""
                                if (snapshot != cachedSnapshot) {
                                    dirSnapshots[file.absolutePath] = snapshot
                                    dirty = true
                                }
                                dirLastModified[file.absolutePath] = lastMod
                            }
                        }
                    }
                }
                dirty
            }
            if (isDirty) {
                refreshTrigger++
            }
        }
    }
    
    // Dialogs
    if (actionDialog == "actions" && nodeToAct != null) {
        AlertDialog(
            onDismissRequest = { actionDialog = null },
            title = { Text("Actions") },
            text = {
                Column {
                    TextButton(onClick = { 
                        newName = File(nodeToAct!!.content.toString()).name
                        actionDialog = "rename"
                    }) { Text("Rename") }
                    TextButton(onClick = { 
                        actionDialog = "delete" 
                    }) { Text("Delete") }
                }
            },
            confirmButton = {}
        )
    }

    if (actionDialog == "rename" && nodeToAct != null) {
        AlertDialog(
            onDismissRequest = { actionDialog = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val oldFile = File(nodeToAct!!.content.toString())
                    val newFile = File(oldFile.parent, newName)
                    if (oldFile.renameTo(newFile)) {
                        refreshTrigger++
                    }
                    actionDialog = null
                    nodeToAct = null
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { actionDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (actionDialog == "delete" && nodeToAct != null) {
        AlertDialog(
            onDismissRequest = { actionDialog = null },
            title = { Text("Delete") },
            text = { Text("Delete '${File(nodeToAct!!.content.toString()).name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    if (File(nodeToAct!!.content.toString()).deleteRecursively()) {
                        refreshTrigger++
                    }
                    actionDialog = null
                    nodeToAct = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { actionDialog = null }) { Text("Cancel") }
            }
        )
    }

    ModalDrawerSheet(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Use Initial pass to catch pinch before LazyColumn consumes it for scrolling
                .pointerInput(Unit) {
                    forEachGesture {
                        awaitPointerEventScope {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    uiScale = (uiScale * zoomChange).coerceIn(0.7f, 2.5f)
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                }
        ) {
            Bonsai(
                tree = tree,
                style = scaledStyle,
                modifier = Modifier.fillMaxSize(),
                onClick = { node ->
                    val file = File(node.content.toString())
                    if (file.isFile) {
                        onFileClick(file.absolutePath)
                    } else {
                        tree.toggleExpansion(node)
                    }
                },
                onLongClick = { node ->
                    nodeToAct = node
                    actionDialog = "actions"
                }
            )

            if (uiScale != 1f) {
                Text(
                    text = "${(uiScale * 100).toInt()}%",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}
