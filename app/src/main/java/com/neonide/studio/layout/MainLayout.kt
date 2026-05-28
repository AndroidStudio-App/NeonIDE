package com.neonide.studio.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonide.studio.R
import com.neonide.studio.ui.components.AppCard
import com.neonide.studio.ui.layout.AppBox
import com.neonide.studio.ui.layout.AppColumn

@Composable
fun mainLayout(
    onSetupDevKit: () -> Unit,
    onCreateProject: () -> Unit,
    onOpenProject: () -> Unit,
    onCloneRepo: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    AppColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Icon(
            painter = painterResource(R.drawable.ic_terminal),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.neonide),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = stringResource(R.string.modern_mobile_development),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(48.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                LayoutItem(
                    stringResource(R.string.new_project),
                    painterResource(R.drawable.ic_add),
                    onCreateProject
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.open_project),
                    painterResource(R.drawable.ic_folder),
                    onOpenProject
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.git_clone),
                    painterResource(R.drawable.ic_folder_tree),
                    onCloneRepo
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.setup_devkit),
                    painterResource(R.drawable.ic_setting),
                    onSetupDevKit
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.terminal),
                    painterResource(R.drawable.ic_terminal),
                    onOpenTerminal
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.settings),
                    painterResource(R.drawable.ic_settings),
                    onOpenSettings
                )
            }
            item {
                LayoutItem(
                    stringResource(R.string.about),
                    painterResource(R.drawable.ic_info),
                    onOpenAbout
                )
            }
        }
    }
}

@Composable
fun LayoutItem(title: String, icon: Painter, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth().height(110.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large
    ) {
        AppBox(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AppColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
