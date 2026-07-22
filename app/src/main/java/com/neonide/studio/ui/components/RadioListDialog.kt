package com.neonide.studio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neonide.studio.ui.layout.AppColumn
import com.neonide.studio.ui.layout.AppRow

@Composable
fun RadioListDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            AppColumn {
                items.forEachIndexed { index, name ->
                    AppRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable { onItemClick(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = index == selectedIndex, onClick = null)
                        Text(
                            name,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    )
}
