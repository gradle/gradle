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
package org.gradle.gradleplugin.userinterface;

import java.io.File;
import java.util.List;

/**
 * This allows this plugin to interact with alternative UIs. Specifically, this has callbacks for IDE's so tell it to
 * edit a project file or the like. This is the 'alternate' UI interaction because it interacts with other UIs (other
 * than the built-in UI).
 *
 * @author mhunsicker
 */
public interface AlternateUIInteraction {
    /**
     * This is called when we should edit the specified files. Open them in the current IDE or some external editor.
     *
     * @param files the files to open
     */
    public void editFiles(List<File> files);

    /**
     * Determines if we can call editFiles. This is not a dynamic answer and should always return either true of false.
     * If you want to change the answer, return true and then handle the files differently in editFiles.
     *
     * @return true if support editing files, false otherwise.
     */
    public boolean doesSupportEditingFiles();
}
