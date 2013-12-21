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
 * Swing implementation of ImportInteraction. This prompts the user for a file via the JFileChooser and handles reporting errors.
 */
public class SwingImportInteraction implements DOM4JSerializer.ImportInteraction {
    private Window parent;

    public SwingImportInteraction(Window parent) {
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

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
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
}
