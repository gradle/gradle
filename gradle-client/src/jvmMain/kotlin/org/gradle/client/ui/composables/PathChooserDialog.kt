package org.gradle.client.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun PathChooserDialog(
    helpText: String = "Select a path",
    selectableFilter: (path: File) -> Boolean = { true },
    choiceMapper: (path: File) -> File = { it },
    onPathChosen: (path: File?) -> Unit,
) = AwtWindow(
    create = {
        object : FileDialog(null as Frame?, helpText, LOAD) {

            init {
                setFilenameFilter { dir, name ->
                    selectableFilter(dir.resolve(name))
                }
            }

            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    if (directory == null || name == null) onPathChosen(null)
                    else onPathChosen(choiceMapper(File(directory).resolve(name)))
                }
            }
        }
    },
    dispose = FileDialog::dispose
)
