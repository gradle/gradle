/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativebinaries.test.cunit.tasks;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Generated the Gradle CUnit launcher: main method and header.
 */
public class GenerateCUnitLauncher extends DefaultTask {
    private File sourceDir;
    private File headerDir;

    @TaskAction
    public void generate() {
        writeToFile(sourceDir, "gradle_cunit_main.c");
        writeToFile(headerDir, "gradle_cunit_register.h");
    }

    private void writeToFile(File directory, String fileName) {
        final File file = new File(directory, fileName);
        try {
            IOUtils.copy(getClass().getResourceAsStream(fileName), new FileWriter(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @OutputDirectory
    public File getSourceDir() {
        return sourceDir;
    }

    public void setSourceDir(File sourceDir) {
        this.sourceDir = sourceDir;
    }

    @OutputDirectory
    public File getHeaderDir() {
        return headerDir;
    }

    public void setHeaderDir(File headerDir) {
        this.headerDir = headerDir;
    }
}
