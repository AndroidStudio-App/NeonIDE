sed -i 's/val navigationBarPadding = with(density) {.*/val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()/' app/src/main/java/com/neonide/studio/filetree/FileTreeDrawer.kt
sed -i '/ViewCompat.getRootWindowInsets(view)/d' app/src/main/java/com/neonide/studio/filetree/FileTreeDrawer.kt
sed -i '/?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())/d' app/src/main/java/com/neonide/studio/filetree/FileTreeDrawer.kt
sed -i '/?.bottom/d' app/src/main/java/com/neonide/studio/filetree/FileTreeDrawer.kt
sed -i '/?.toDp() ?: 0.dp/d' app/src/main/java/com/neonide/studio/filetree/FileTreeDrawer.kt
