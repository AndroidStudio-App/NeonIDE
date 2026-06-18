package com.neonide.studio.filetree

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.BonsaiStyle
import cafe.adriel.bonsai.core.node.BranchNode
import com.neonide.studio.R
import com.neonide.studio.app.utils.SafeFileDeleter
import com.neonide.studio.utils.ApkInstallUtils
import com.neonide.studio.utils.Divider.horizontalDivider
import com.neonide.studio.utils.PersistedBoolean
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@Composable
fun FileTreeDrawer(rootPath: String, onFileClick: (String) -> Unit) {
    val context = LocalContext.current
    val rootPathOkio = remember(rootPath) { rootPath.toPath() }
    var refreshTrigger by remember { mutableStateOf(0) }
    var uiScale by remember { mutableStateOf(1.5f) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf<ClipboardEntry?>(null) }
    var contextTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var inlineMode by remember { mutableStateOf<InlineMode>(InlineMode.None) }
    var inlineText by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    val prefs = remember {
        context.getSharedPreferences("filetree_prefs", android.content.Context.MODE_PRIVATE)
    }
    val isCompactMode = remember { PersistedBoolean(prefs, "compact_mode", true) }
    val searchRegex = remember { PersistedBoolean(prefs, "search_regex", false) }
    val searchCaseSensitive = remember { PersistedBoolean(prefs, "case_sensitive", false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val lastTouch = remember { intArrayOf(0, 0) }

    val tree = CompactFileSystemTree(
        rootPath = rootPathOkio,
        fileSystem = FileSystem.SYSTEM,
        selfInclude = true,
        refreshTrigger = refreshTrigger,
        uiScale = uiScale,
        compactMode = isCompactMode.value
    )

    LaunchedEffect(rootPathOkio) {
        tree.expandRoot()
    }

    val dirSnapshots = remember(rootPath) { mutableMapOf<String, DirSnapshot>() }

    LaunchedEffect(rootPathOkio, refreshTrigger) {
        while (true) {
            delay(1500)
            val isDirty = withContext(Dispatchers.IO) {
                var dirty = false
                val nodesSnapshot = tree.nodes.toList()
                for (node in nodesSnapshot) {
                    if (node is BranchNode<*> && node.isExpanded) {
                        val file = File(node.content.toString())
                        if (file.isDirectory) {
                            val lastMod = file.lastModified()
                            val cached = dirSnapshots[file.absolutePath]
                            if (cached == null || cached.lastModified != lastMod) {
                                val snapshot = file.listFiles()
                                    ?.sortedBy { it.name }
                                    ?.joinToString { "${it.name}:${it.isDirectory}" } ?: ""
                                if (cached == null || cached.listing != snapshot) {
                                    dirSnapshots[file.absolutePath] = DirSnapshot(lastMod, snapshot)
                                    dirty = true
                                }
                            }
                        }
                    }
                }
                dirty
            }
            if (isDirty) refreshTrigger++
        }
    }

    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scaledStyle = remember(uiScale, selectedBg, onSurface) {
        BonsaiStyle<Path>(
            nodeCollapsedIcon = { null },
            nodeExpandedIcon = { null },
            nodeSelectedBackgroundColor = selectedBg,
            nodeNameTextStyle = TextStyle(fontSize = 12.sp * uiScale, color = onSurface),
            nodeNameStartPadding = 4.dp * uiScale,
            toggleIcon = { node ->
                if (node is BranchNode) {
                    painterResource(
                        if (node.isExpanded) {
                            R.drawable.ic_chevron_down
                        } else {
                            R.drawable.ic_chevron_right
                        }
                    )
                } else {
                    null
                }
            },
            toggleIconSize = 16.dp * uiScale,
            toggleIconRotationDegrees = 0f,
            useHorizontalScroll = true
        )
    }
    ModalDrawerSheet(
        drawerShape = RectangleShape,
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            horizontalDivider()
            FileTreeToolbar(
                onCollapseAll = { tree.collapseAll() },
                onExpandAll = { tree.expandAll() },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) searchQuery = ""
                },
                isCompactMode = isCompactMode.value,
                onToggleCompact = { isCompactMode.value = !isCompactMode.value },
                searchRegex = searchRegex.value,
                onToggleRegex = { searchRegex.value = !searchRegex.value },
                searchCaseSensitive = searchCaseSensitive.value,
                onToggleCaseSensitive = { searchCaseSensitive.value = !searchCaseSensitive.value },
                menuExpanded = menuExpanded,
                onToggleMenu = { menuExpanded = !menuExpanded },
                onDismissMenu = { menuExpanded = false },
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it }
            )

            horizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.firstOrNull()?.let {
                                    lastTouch[0] = it.position.x.toInt()
                                    lastTouch[1] = it.position.y.toInt()
                                }
                                if (event.changes.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        uiScale = (uiScale * zoomChange).coerceIn(0.7f, 2.5f)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                if (searchQuery.isNotEmpty()) {
                    SearchResultsList(
                        rootPath = rootPathOkio,
                        query = searchQuery,
                        uiScale = uiScale,
                        useRegex = searchRegex.value,
                        caseSensitive = searchCaseSensitive.value,
                        onFileClick = { path -> onFileClick(path) },
                        onFolderLongClick = { path, name ->
                            contextTarget = ContextMenuTarget(path, name, true)
                        },
                        onFileLongClick = { path, name ->
                            contextTarget = ContextMenuTarget(path, name, false)
                        }
                    )
                } else {
                    Bonsai(
                        tree = tree,
                        style = scaledStyle,
                        modifier = Modifier.fillMaxSize(),
                        onClick = { node ->
                            val file = File(node.content.toString())
                            if (file.isFile) {
                                if (file.extension.equals("apk", ignoreCase = true)) {
                                    ApkInstallUtils.installApk(context, file)
                                }
                                onFileClick(file.absolutePath)
                            } else {
                                tree.toggleExpansion(node)
                            }
                        },
                        onLongClick = { node ->
                            tree.clearSelection()
                            tree.selectNode(node)
                            val file = File(node.content.toString())
                            contextTarget = ContextMenuTarget(
                                path = node.content,
                                name = file.name,
                                isDirectory = file.isDirectory
                            )
                            contextMenuOffset = IntOffset(lastTouch[0], lastTouch[1])
                        }
                    )
                }

                if (uiScale != 1f) {
                    Text(
                        text = "${(uiScale * 100).toInt()}%",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
            }

            ClipboardIndicatorBar(
                clipboard = clipboard,
                onClear = { clipboard = null }
            )
        }
    }

    FileTreeContextMenu(
        target = contextTarget,
        clipboard = clipboard,
        offset = contextMenuOffset,
        onDismiss = {
            contextTarget = null
            tree.clearSelection()
        },
        onNewFile = {
            val parentPath = if (it.isDirectory) it.path else (it.path.parent ?: it.path)
            inlineMode = InlineMode.NewFile(parentPath)
            inlineText = ""
            inlineError = null
        },
        onNewDirectory = {
            val parentPath = if (it.isDirectory) it.path else (it.path.parent ?: it.path)
            inlineMode = InlineMode.NewDirectory(parentPath)
            inlineText = ""
            inlineError = null
        },
        onCut = {
            clipboard = ClipboardEntry(it.path, it.name, it.isDirectory, ClipboardMode.CUT)
        },
        onCopy = {
            clipboard = ClipboardEntry(it.path, it.name, it.isDirectory, ClipboardMode.COPY)
        },
        onPaste = {
            val clip = clipboard ?: return@FileTreeContextMenu
            val targetDir = if (it.isDirectory) it.path else (it.path.parent ?: it.path)
            performPaste(clip, targetDir)
            clipboard = null
            refreshTrigger++
        },
        onRename = {
            inlineMode = InlineMode.Rename(it.path)
            inlineText = it.name
            inlineError = null
        },
        onDelete = {
            deleteTarget = it
        },
        onCopyPath = {}
    )

    when (val mode = inlineMode) {
        is InlineMode.NewFile -> {
            InlineInputDialog(
                title = stringResource(R.string.new_file),
                value = inlineText,
                onValueChange = {
                    inlineText = it
                    inlineError = null
                },
                errorMessage = inlineError,
                onConfirm = {
                    if (inlineText.isNotEmpty()) {
                        val file = File(mode.parentPath.toString(), inlineText)
                        if (file.exists()) {
                            inlineError = context.getString(R.string.file_or_folder_exists)
                        } else {
                            file.parentFile?.mkdirs()
                            file.createNewFile()
                            refreshTrigger++
                            inlineMode = InlineMode.None
                        }
                    }
                },
                onDismiss = {
                    inlineMode = InlineMode.None
                    inlineError = null
                }
            )
        }

        is InlineMode.NewDirectory -> {
            InlineInputDialog(
                title = stringResource(R.string.new_directory),
                value = inlineText,
                onValueChange = {
                    inlineText = it
                    inlineError = null
                },
                errorMessage = inlineError,
                onConfirm = {
                    if (inlineText.isNotEmpty()) {
                        val file = File(mode.parentPath.toString(), inlineText)
                        if (file.exists()) {
                            inlineError = context.getString(R.string.file_or_folder_exists)
                        } else {
                            file.mkdirs()
                            refreshTrigger++
                            inlineMode = InlineMode.None
                        }
                    }
                },
                onDismiss = {
                    inlineMode = InlineMode.None
                    inlineError = null
                }
            )
        }

        is InlineMode.Rename -> {
            InlineInputDialog(
                title = stringResource(R.string.rename),
                value = inlineText,
                onValueChange = {
                    inlineText = it
                    inlineError = null
                },
                errorMessage = inlineError,
                onConfirm = {
                    if (inlineText.isNotEmpty()) {
                        val oldFile = File(mode.path.toString())
                        val newFile = File(oldFile.parent, inlineText)
                        if (newFile.exists()) {
                            inlineError = context.getString(R.string.file_or_folder_exists)
                        } else {
                            if (oldFile.renameTo(newFile)) refreshTrigger++
                            inlineMode = InlineMode.None
                        }
                    }
                },
                onDismiss = {
                    inlineMode = InlineMode.None
                    inlineError = null
                }
            )
        }

        InlineMode.None -> {}
    }

    if (deleteTarget != null) {
        val target = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            text = { Text(stringResource(R.string.delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    SafeFileDeleter.deleteRecursively(File(target.path.toString()))
                    refreshTrigger++
                    deleteTarget = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    deleteTarget = null
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private data class DirSnapshot(val lastModified: Long, val listing: String)

internal sealed interface InlineMode {
    data object None : InlineMode
    data class NewFile(val parentPath: Path) : InlineMode
    data class NewDirectory(val parentPath: Path) : InlineMode
    data class Rename(val path: Path) : InlineMode
}

private fun performPaste(clip: ClipboardEntry, targetDir: Path) {
    val source = File(clip.path.toString())
    val dest = File(targetDir.toString(), clip.name)
    when (clip.mode) {
        ClipboardMode.CUT -> source.renameTo(dest)

        ClipboardMode.COPY -> {
            if (source.isDirectory) {
                source.copyRecursively(dest)
            } else {
                source.copyTo(dest, overwrite = false)
            }
        }
    }
}
