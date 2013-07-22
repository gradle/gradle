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
package org.gradle.gradleplugin.foundation;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.gradleplugin.foundation.settings.DOM4JSettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsNode;
import org.gradle.gradleplugin.foundation.settings.SettingsSerializable;

import javax.swing.filechooser.FileFilter;
import java.io.*;

/**
 * This saves and reads thing to and from DOM structures.
 */
public class DOM4JSerializer {
    private static final Logger LOGGER = Logging.getLogger(DOM4JSerializer.class);

    /**
     * Implement this when you export a file. This allows to interactively ask the user (or automated test) questions as we need answers.
     */
    public interface ExportInteraction {
        /**
         * This is called when you should ask the user for a destination file of a save.
         *
         * @param fileFilter describes the allowed file type. Suitable for passing to JFileChooser.
         * @return a file to save to or null to cancel.
         */
        public File promptForFile(FileFilter fileFilter);

        /**
         * Report an error that occurred. The save failed.
         *
         * @param error the error message.
         */
        public void reportError(String error);

        /**
         * The file already exists. Confirm whether or not you want to overwrite it.
         *
         * @param file the file in question
         * @return true to overwrite it, false not to.
         */
        boolean confirmOverwritingExistingFile(File file);
    }

    /**
     * Call this to save the JDOMSerializable to a file. This handles confirming overwriting an existing file as well as ensuring the extension is correct based on the passed in fileFilter.
     */
    public static void exportToFile(String rootElementTag, ExportInteraction exportInteraction, ExtensionFileFilter fileFilter, SettingsSerializable... serializables) {
        File file = promptForFile(exportInteraction, fileFilter);
        if (file == null) {
            //the user canceled.
            return;
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not write to file: " + file.getAbsolutePath(), e);
            exportInteraction.reportError("Could not write to file: " + file.getAbsolutePath());
            return;
        }

        try {
            XMLWriter xmlWriter = new XMLWriter(fileOutputStream, OutputFormat.createPrettyPrint());

            Document document = DocumentHelper.createDocument();
            Element rootElement = document.addElement(rootElementTag);
            DOM4JSettingsNode settingsNode = new DOM4JSettingsNode(rootElement);
            for (int index = 0; index < serializables.length; index++) {
                SettingsSerializable serializable = serializables[index];
                try {  //don't let a single serializer stop the entire thing from being written in.
                    serializable.serializeOut(settingsNode);
                } catch (Exception e) {
                    LOGGER.error("serializing", e);
                }
            }
            xmlWriter.write(document);
        } catch (Throwable t) {
            LOGGER.error("Failed to save", t);
            exportInteraction.reportError("Internal error. Failed to save.");
        } finally {
            closeQuietly(fileOutputStream);
        }
    }

    public static void exportToFile(ExportInteraction exportInteraction, ExtensionFileFilter fileFilter, DOM4JSettingsNode settingsNode) {
        File file = promptForFile(exportInteraction, fileFilter);
        if (file == null) {
            //the user canceled.
            return;
        }

        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not write to file: " + file.getAbsolutePath(), e);
            exportInteraction.reportError("Could not write to file: " + file.getAbsolutePath());
            return;
        }

        try {
            XMLWriter xmlWriter = new XMLWriter(fileOutputStream, OutputFormat.createPrettyPrint());
            Element rootElement = settingsNode.getElement();
            rootElement.detach();

            Document document = DocumentHelper.createDocument(rootElement);

            xmlWriter.write(document);
        } catch (Throwable t) {
            LOGGER.error("Internal error. Failed to save.", t);
            exportInteraction.reportError("Internal error. Failed to save.");
        } finally {
            closeQuietly(fileOutputStream);
        }
    }

    /**
     * This prompts the user for a file. It may exist, so we have to confirm overwriting it. This will sit in a loop until the user cancels or gives us a valid file. This also makes sure the extension
     * is correct.
     */
    private static File promptForFile(ExportInteraction exportInteraction, ExtensionFileFilter fileFilter) {
        boolean promptAgain = false;
        File file = null;
        int counter = 0;
        do {
            promptAgain = false;
            file = exportInteraction.promptForFile(fileFilter);
            if (file != null) {
                file = ensureFileHasCorrectExtensionAndCase(file, fileFilter.getExtension());

                if (file.exists()) {
                    promptAgain = !exportInteraction.confirmOverwritingExistingFile(file);
                }
            }

            counter++;
        } while (promptAgain && counter < 1000);   //the counter is just to make sure any tests that use this don't get stuck in an infinite loop (they may return the same thing from promptForFile).

        return file;
    }

    //

    /**
     * Implement this when you import a file. This allows to interactively ask the user (or automated test) questions as we need answers.
     */
    public interface ImportInteraction {
        /**
         * This is called when you should ask the user for a source file to read.
         *
         * @return a file to read or null to cancel.
         */
        public File promptForFile(FileFilter fileFilters);

        /**
         * Report an error that occurred. The read failed.
         *
         * @param error the error message.
         */
        public void reportError(String error);
    }

    /**
     * Call this to read the JDOMSerializable from a file.
     */
    public static boolean importFromFile(ImportInteraction importInteraction, FileFilter fileFilter, SettingsSerializable... serializables) {
        SettingsNode settings = readSettingsFile(importInteraction, fileFilter);
        if (settings == null) {
            return false;
        }

        for (int index = 0; index < serializables.length; index++) {
            SettingsSerializable serializable = serializables[index];

            try {  //don't let a single serializer stop the entire thing from being read in.
                serializable.serializeIn(settings);
            } catch (Exception e) {
                LOGGER.error("importing file", e);
            }
        }

        return true;
    }

    public static DOM4JSettingsNode readSettingsFile(ImportInteraction importInteraction, FileFilter fileFilter) {
        File file = importInteraction.promptForFile(fileFilter);
        if (file == null) {
            return null;
        }

        if (!file.exists()) {
            importInteraction.reportError("File does not exist: " + file.getAbsolutePath());
            return null;  //we should really sit in a loop until they cancel or give us a valid file.
        }

        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(file);

            return new DOM4JSettingsNode(document.getRootElement());
        } catch (Throwable t) {
            LOGGER.error("Unable to read file: " + file.getAbsolutePath(), t);
            importInteraction.reportError("Unable to read file: " + file.getAbsolutePath());
            return null;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            LOGGER.error("Closing", e);
        }
    }

    /**
     * A convenience function that ensures that the specified file does have a specific extension. You have to tell us that extension.
     */
    private static File ensureFileHasCorrectExtensionAndCase(File file, String requiredExtension) {
        String name = file.getName();
        if (!name.toLowerCase().endsWith(requiredExtension.toLowerCase())) {
            return new File(file.getParentFile(), name + requiredExtension);
        }

        return file;   //it already ends with the correct extension.
    }

    public static DOM4JSettingsNode createBlankSettings() {
        Document document = DocumentHelper.createDocument();
        Element rootElement = document.addElement("root");
        DOM4JSettingsNode settings = new DOM4JSettingsNode(rootElement);
        return settings;
    }
}
