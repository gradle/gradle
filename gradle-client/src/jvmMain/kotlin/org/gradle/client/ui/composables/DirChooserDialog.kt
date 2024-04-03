package org.gradle.client.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import javax.swing.JFileChooser

@Composable
fun DirChooserDialog(
    helpText: String,
    showHiddenFiles: Boolean = false,
    onDirChosen: (dir: File?) -> Unit,
) {
    LaunchedEffect(Unit) {
        val chooser = JFileChooser()
        chooser.dialogTitle = helpText
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        chooser.isMultiSelectionEnabled = false
        chooser.isFileHidingEnabled = !showHiddenFiles
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onDirChosen(chooser.selectedFile)
        } else {
            onDirChosen(null)
        }
    }
}
