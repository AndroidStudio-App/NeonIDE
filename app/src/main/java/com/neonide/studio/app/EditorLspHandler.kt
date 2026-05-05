package com.neonide.studio.app

import android.view.View
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.neonide.studio.R
import com.neonide.studio.app.bottomsheet.model.BottomSheetViewModel
import com.neonide.studio.app.bottomsheet.model.NavigationItem
import com.neonide.studio.app.lsp.EditorLspController
import io.github.rosemoe.sora.event.CreateContextMenuEvent
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.io.File
import java.net.URI

/**
 * Controller for handling LSP-related actions (Definition, References, Hover) and context menus.
 */
class EditorLspHandler(
    private val activity: SoraEditorActivityK,
    private val editor: CodeEditor,
    private val lspController: EditorLspController,
    private val bottomSheetVm: BottomSheetViewModel,
    private val uiScope: CoroutineScope
) {

    companion object {
        private const val MENU_ID_DEFINITION = 1001
        private const val MENU_ID_REFERENCES = 1002
        private const val MENU_ID_HOVER = 1003
        private const val TAB_INDEX_REFERENCES = 5
    }

    fun onContextMenuCreated(event: CreateContextMenuEvent) {
        event.menu.add(0, MENU_ID_DEFINITION, 0, activity.getString(R.string.acs_menu_go_to_definition))
            .setOnMenuItemClickListener {
                handleGoToDefinition(event.position.line, event.position.column)
                true
            }
        event.menu.add(0, MENU_ID_REFERENCES, 0, activity.getString(R.string.acs_menu_find_references))
            .setOnMenuItemClickListener {
                handleFindReferences(event.position.line, event.position.column)
                true
            }

        event.menu.add(0, MENU_ID_HOVER, 0, "Show documentation")
            .setOnMenuItemClickListener {
                handleShowHover(event.position.line, event.position.column)
                true
            }
    }

    fun handleGoToDefinition(line: Int, column: Int) {
        val currentFile = activity.currentFile ?: return
        
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = DefinitionParams().apply {
            textDocument = TextDocumentIdentifier(currentFile.toURI().toString())
            position = Position(line, column)
        }
        rm.definition(params)?.thenAccept { result ->
            uiScope.launch(Dispatchers.Main) {
                val locations = if (result.isLeft) {
                    result.left
                } else {
                    result.right.map { Location(it.targetUri, it.targetRange) }
                }

                if (locations.isEmpty()) {
                    Toast.makeText(activity, "No definition found", Toast.LENGTH_SHORT).show()
                } else if (locations.size == 1) {
                    val loc = locations[0]
                    activity.navigateTo(loc.uri, loc.range.start.line, loc.range.start.character)
                } else {
                    val items = locations.map { loc ->
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "${File(URI.create(loc.uri)).name}:${loc.range.start.line + 1}")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    fun handleShowHover(line: Int, column: Int) {
        val currentFile = activity.currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return

        val params = HoverParams().apply {
            textDocument = TextDocumentIdentifier(currentFile.toURI().toString())
            position = Position(line, column)
        }

        lspEditor.hoverWindow?.HOVER_TOOLTIP_SHOW_TIMEOUT = 0L

        rm.hover(params)?.thenAccept { hover ->
            lspEditor.showHover(hover)
        }
    }

    fun handleFindReferences(line: Int, column: Int) {
        val currentFile = activity.currentFile ?: return
        val lspEditor = lspController.currentEditor() ?: return
        val rm = lspEditor.requestManager ?: return
        val params = ReferenceParams().apply {
            textDocument = TextDocumentIdentifier(currentFile.toURI().toString())
            position = Position(line, column)
            context = ReferenceContext(true)
        }
        rm.references(params)?.thenAccept { locationsNullable ->
            val locations = locationsNullable?.filterNotNull() ?: emptyList()
            uiScope.launch(Dispatchers.Main) {
                if (locations.isEmpty()) {
                    Toast.makeText(activity, "No references found", Toast.LENGTH_SHORT).show()
                } else {
                    val items = locations.map { loc ->
                        val fileName = File(URI.create(loc.uri)).name
                        val lineContent = if (loc.uri == currentFile.toURI().toString()) {
                            editor.text.getLineString(loc.range.start.line).trim()
                        } else {
                            ""
                        }
                        NavigationItem(loc.uri, loc.range.start.line, loc.range.start.character, "$fileName:${loc.range.start.line + 1} $lineContent")
                    }
                    bottomSheetVm.setNavigationResults(items)
                    showNavigationTab()
                }
            }
        }
    }

    private fun showNavigationTab() {
        val sheet = activity.findViewById<View>(R.id.acs_bottom_sheet)
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        val viewPager = activity.findViewById<ViewPager2>(R.id.acs_bottom_sheet_pager)
        viewPager.currentItem = TAB_INDEX_REFERENCES
    }
}
