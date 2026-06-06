package com.neonide.studio.filetree

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppIcon
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow
import com.neonide.studio.utils.Divider.horizontalDivider
import okio.Path

enum class ClipboardMode { CUT, COPY }

data class ClipboardEntry(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val mode: ClipboardMode
)

@Composable
fun ClipboardIndicatorBar(
    clipboard: ClipboardEntry?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = clipboard != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        val entry = clipboard ?: return@AnimatedVisibility
        val modeLabel = if (entry.mode ==
            ClipboardMode.CUT
        ) {
            stringResource(R.string.cut)
        } else {
            stringResource(R.string.copy)
        }
        val iconTint = MaterialTheme.colorScheme.tertiary
        val iconRes = if (entry.mode == ClipboardMode.CUT) R.drawable.ic_cut else R.drawable.ic_copy

        AppColumn {
            horizontalDivider()
            AppRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    painter = painterResource(iconRes),
                    tint = iconTint,
                    size = 16.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.clipboard_hint, modeLabel, entry.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).basicMarquee()
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(20.dp)
                ) {
                    AppIcon(
                        painter = painterResource(R.drawable.ic_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 14.dp
                    )
                }
            }
        }
    }
}
