package com.neonide.studio.app

import com.neonide.studio.R

class EditorDialogManager(private val dialogHelper: EditorDialogHelper) {

    fun handleDialogAction(itemId: Int): Boolean {
        return when (itemId) {
            R.id.sora_switch_language -> {
                dialogHelper.chooseLanguage()
                true
            }
            R.id.sora_switch_colors -> {
                dialogHelper.chooseTheme()
                true
            }
            R.id.sora_switch_typeface -> {
                dialogHelper.chooseTypeface()
                true
            }
            else -> false
        }
    }
}
