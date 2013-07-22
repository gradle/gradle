/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.openapi;

import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;

import java.io.File;

/**
 * Implementation of AlternateUIInteractionVersion1 for testing purposes.
 * This would lend itself well for mocking. 
  */
public class TestAlternateUIInteractionVersion1 implements AlternateUIInteractionVersion1 {

    private boolean supportsEditingOpeningFiles;

    public TestAlternateUIInteractionVersion1() {
    }

    public TestAlternateUIInteractionVersion1(boolean supportsEditingOpeningFiles) {
        this.supportsEditingOpeningFiles = supportsEditingOpeningFiles;
    }

    public void openFile(File file, int line) {

    }

    public void editFile(File file, int line) {

    }

    public boolean doesSupportEditingOpeningFiles() {
        return supportsEditingOpeningFiles;
    }

    public void aboutToExecuteCommand(String fullCommandLine) {

    }
}