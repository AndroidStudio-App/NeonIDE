package com.neonide.studio.filetree

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.bonsai.core.node.Branch
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Leaf
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.core.tree.TreeScope
import okio.FileSystem
import okio.Path

@Composable
fun CompactFileSystemTree(
    rootPath: Path,
    fileSystem: FileSystem,
    selfInclude: Boolean = false,
    refreshTrigger: Int = 0,
    uiScale: Float = 1f,
    compactMode: Boolean = true
): Tree<Path> = Tree {
    CompactFileSystemTreeContent(
        rootPath = rootPath,
        fileSystem = fileSystem,
        selfInclude = selfInclude,
        refreshTrigger = refreshTrigger,
        uiScale = uiScale,
        compactMode = compactMode
    )
}

@Composable
private fun TreeScope.CompactFileSystemTreeContent(
    rootPath: Path,
    fileSystem: FileSystem,
    selfInclude: Boolean,
    refreshTrigger: Int,
    uiScale: Float,
    compactMode: Boolean
) {
    if (selfInclude) {
        androidx.compose.runtime.key(rootPath.toString()) {
            CompactFileSystemNode(rootPath, fileSystem, refreshTrigger, uiScale, compactMode)
        }
    } else {
        fileSystem
            .listOrNull(rootPath)
            ?.sortedWith(
                compareBy<Path> { !fileSystem.metadata(it).isDirectory }
                    .thenBy { it.name.lowercase() }
            )?.forEach { path ->
                androidx.compose.runtime.key(path.toString()) {
                    CompactFileSystemNode(path, fileSystem, refreshTrigger, uiScale, compactMode)
                }
            }
    }
}

@Composable
private fun TreeScope.CompactFileSystemNode(
    path: Path,
    fileSystem: FileSystem,
    refreshTrigger: Int,
    uiScale: Float,
    compactMode: Boolean
) {
    val metadata = fileSystem.metadata(path)
    if (!metadata.isDirectory) {
        Leaf(
            content = path,
            key = path.toString(),
            name = path.name,
            customIcon = { node ->
                val ext = node.content.name.substringAfterLast('.', "")
                Image(
                    painter = painterResource(iconForExtension(ext)),
                    contentDescription = node.content.name,
                    modifier = Modifier.size(16.dp * uiScale)
                )
            }
        )
        return
    }

    val folderIcon: @Composable (Boolean) -> Unit = { isExpanded ->
        val resId = iconForFolder(path.name, isExpanded)
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.size(16.dp * uiScale)
        )
    }

    if (compactMode) {
        val (mergedPath, segments) = resolveMergedChain(path, fileSystem)
        val displayName = segments.joinToString(".")

        if (segments.size > 1) {
            Branch(
                content = mergedPath,
                key = mergedPath.toString(),
                name = displayName,
                customIcon = { node -> folderIcon(node is BranchNode && node.isExpanded) },
                customName = {
                    val annotated = buildAnnotatedString {
                        segments.forEachIndexed { index, segment ->
                            if (index > 0) {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline)) {
                                    append(".")
                                }
                            }
                            val color = if (index == 0 || index == segments.lastIndex) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            withStyle(SpanStyle(color = color)) { append(segment) }
                        }
                    }
                    Text(text = annotated, fontSize = 12.sp * uiScale)
                }
            ) {
                CompactFileSystemTreeContent(
                    rootPath = mergedPath,
                    fileSystem = fileSystem,
                    selfInclude = false,
                    refreshTrigger = refreshTrigger,
                    uiScale = uiScale,
                    compactMode = compactMode
                )
            }
        } else {
            Branch(
                content = path,
                key = path.toString(),
                name = path.name,
                customIcon = { node -> folderIcon(node is BranchNode && node.isExpanded) }
            ) {
                CompactFileSystemTreeContent(
                    rootPath = path,
                    fileSystem = fileSystem,
                    selfInclude = false,
                    refreshTrigger = refreshTrigger,
                    uiScale = uiScale,
                    compactMode = compactMode
                )
            }
        }
    } else {
        Branch(
            content = path,
            key = path.toString(),
            name = path.name,
            customIcon = { node -> folderIcon(node is BranchNode && node.isExpanded) }
        ) {
            CompactFileSystemTreeContent(
                rootPath = path,
                fileSystem = fileSystem,
                selfInclude = false,
                refreshTrigger = refreshTrigger,
                uiScale = uiScale,
                compactMode = compactMode
            )
        }
    }
}

private fun resolveMergedChain(startPath: Path, fileSystem: FileSystem): Pair<Path, List<String>> {
    val segments = mutableListOf(startPath.name)
    var current = startPath
    while (true) {
        val children = fileSystem.listOrNull(current) ?: break
        if (children.size != 1) break
        val child = children[0]
        if (!fileSystem.metadata(child).isDirectory) break
        current = child
        segments.add(current.name)
    }
    return current to segments
}
