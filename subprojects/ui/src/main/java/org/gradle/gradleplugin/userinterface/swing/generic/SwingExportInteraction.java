/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.gradleplugin.foundation.DOM4JSerializer;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.io.File;

/**
 * Swing implementation of ExportInteraction. This prompts the user for a file via the JFileChooser and handles reporting errors.
 */
public class SwingExportInteraction implements DOM4JSerializer.ExportInteraction {
    private Window parent;

    public SwingExportInteraction(Window parent) {
        this.parent = parent;
    }

    /**
     * This is called when you should ask the user for a source file to read.
     *
     * @return a file to read or null to cancel.
     */
    public File promptForFile(FileFilter fileFilter) {
        JFileChooser chooser = new JFileChooser();
        chooser.addChoosableFileFilter(fileFilter);

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        return chooser.getSelectedFile();
    }

    /**
     * Report an error that occurred. The read failed.
     *
     * @param error the error message.
     */
    public void reportError(String error) {
        JOptionPane.showMessageDialog(parent, error, "Error", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * The file already exists. Confirm whether or not you want to overwrite it.
     *
     * @param file the file in question
     * @return true to overwrite it, false not to.
     */
    public boolean confirmOverwritingExistingFile(File file) {
        int result = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(parent),
                "The file '" + file.getAbsolutePath() + "' already exists. Overwrite?", "Confirm Overwriting File",
                JOptionPane.YES_NO_OPTION);
        return result == JOptionPane.YES_OPTION;
    }
}