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
package org.gradle.openapi.external.ui;

import java.io.File;

/**
 * This is how the gradle UI panel interacts with the UI that is holding it.
 *
 * This is a mirror of AlternateUIInteraction inside Gradle, but this is meant to aid backward and forward compatibility by shielding you from direct changes within gradle.
 * @deprecated No replacement
 */
@Deprecated
public interface AlternateUIInteractionVersion1 {
    /**
     * Notification that you should open the specified file and go to the specified line. Its up to the application to determine if this file should be opened for editing or simply displayed. The
     * difference comes into play for things like xml or html files where a user may want to open them in a browser vs a source code file where they may want to open it directly in an IDE.
     *
     * @param file the file to edit
     * @param line the line to go to. -1 if no line is specified.
     */
    public void openFile(File file, int line);

    /**
     * This is called when we should open the specified file for editing. This version explicitly wants them edited versus just opened.
     *
     * @param file the file to open
     * @param line the line to go to. -1 if no line is specified.
     */
    public void editFile(File file, int line);

    /**
     * Determines if we can call editFiles or openFile. This is not a dynamic answer and should always return either true of false. If you want to change the answer, return true and then handle the
     * files differently in editFiles.
     *
     * @return true if support editing files, false otherwise.
     */
    public boolean doesSupportEditingOpeningFiles();

    /**
     * Notification that a command is about to be executed. This is mostly useful for IDE's that may need to save their files.
     *
     * @param fullCommandLine the command that's about to be executed.
     */
    public void aboutToExecuteCommand(String fullCommandLine);
}
