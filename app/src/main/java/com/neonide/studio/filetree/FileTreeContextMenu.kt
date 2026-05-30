package com.neonide.studio.filetree

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.divider.horizontalDivider
import kotlinx.coroutines.launch
import okio.Path

data class ContextMenuTarget(val path: Path, val name: String, val isDirectory: Boolean)

@Composable
fun FileTreeContextMenu(
    target: ContextMenuTarget?,
    clipboard: ClipboardEntry?,
    onDismiss: () -> Unit,
    onNewFile: (ContextMenuTarget) -> Unit,
    onNewDirectory: (ContextMenuTarget) -> Unit,
    onCut: (ContextMenuTarget) -> Unit,
    onCopy: (ContextMenuTarget) -> Unit,
    onPaste: (ContextMenuTarget) -> Unit,
    onRename: (ContextMenuTarget) -> Unit,
    onDelete: (ContextMenuTarget) -> Unit,
    onCopyPath: (ContextMenuTarget) -> Unit,
    offset: IntOffset = IntOffset(0, 0)
) {
    if (target == null) return

    val canPaste = clipboard != null
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        AppColumn(
            modifier = Modifier
                .width(200.dp)
                .shadow(8.dp, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 4.dp)
        ) {
            ContextMenuItem(
                iconRes = R.drawable.ic_file_add,
                label = stringResource(R.string.new_file),
                onClick = {
                    val t = target
                    onDismiss()
                    onNewFile(t)
                }
            )
            ContextMenuItem(
                iconRes = R.drawable.ic_folder_add,
                label = stringResource(R.string.new_directory),
                onClick = {
                    val t = target
                    onDismiss()
                    onNewDirectory(t)
                }
            )

            horizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

            ContextMenuItem(
                iconRes = R.drawable.ic_cut,
                label = stringResource(R.string.cut),
                onClick = {
                    val t = target
                    onDismiss()
                    onCut(t)
                }
            )
            ContextMenuItem(
                iconRes = R.drawable.ic_copy,
                label = stringResource(R.string.copy),
                onClick = {
                    val t = target
                    onDismiss()
                    onCopy(t)
                }
            )
            ContextMenuItem(
                iconRes = R.drawable.ic_paste,
                label = stringResource(R.string.paste),
                enabled = canPaste,
                onClick = {
                    val t = target
                    onDismiss()
                    onPaste(t)
                }
            )

            horizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

            ContextMenuItem(
                iconRes = R.drawable.ic_rename,
                label = stringResource(R.string.rename),
                onClick = {
                    val t = target
                    onDismiss()
                    onRename(t)
                }
            )
            ContextMenuItem(
                iconRes = R.drawable.ic_delete,
                label = stringResource(R.string.delete),
                onClick = {
                    val t = target
                    onDismiss()
                    onDelete(t)
                }
            )

            horizontalDivider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))

            ContextMenuItem(
                iconRes = R.drawable.ic_copy,
                label = stringResource(R.string.copy_path),
                onClick = {
                    val t = target
                    onDismiss()
                    onCopyPath(t)
                    scope.launch {
                        val clipData = ClipData.newPlainText("path", t.path.toString())
                        clipboardManager.setClipEntry(ClipEntry(clipData))
                    }
                }
            )
        }
    }
}

@Composable
private fun ContextMenuItem(
    iconRes: Int,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline

    AppRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(
            painter = painterResource(iconRes),
            tint = Color.Unspecified,
            size = 20.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = textColor
        )
    }
}
