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

package org.gradle.nativeplatform.test.cunit.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.internal.GFileUtils;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;

/**
 * Generated the Gradle CUnit launcher: main method and header.
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class GenerateCUnitLauncher extends DefaultTask {
    private File sourceDir;
    private File headerDir;

    @TaskAction
    public void generate() {
        writeToFile(sourceDir, "gradle_cunit_main.c");
        writeToFile(headerDir, "gradle_cunit_register.h");
    }

    private void writeToFile(File directory, String fileName) {
        final File file = new File(directory, fileName);
        GFileUtils.copyURLToFile(getClass().getResource(fileName), file);
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
