/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.fixtures.app;

import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.test.fixtures.file.TestFile;

import java.util.List;

/**
 * An element containing zero or more source files.
 */
public abstract class SourceElement extends Element {
    /**
     * Returns the files associated with this element, possibly none.
     */
    public abstract List<SourceFile> getFiles();

    /**
     * Writes the source files of this element to the given project, using the Gradle convention for source layout.
     */
    public void writeToProject(TestFile projectDir) {
        TestFile srcDir = projectDir.file("src/main");
        for (SourceFile sourceFile : getFiles()) {
            sourceFile.writeToDir(srcDir);
        }
    }

    /**
     * Writes the source files of this element to the given source directory.
     */
    public void writeToSourceDir(TestFile sourceDir) {
        for (SourceFile sourceFile : getFiles()) {
            sourceFile.writeToFile(sourceDir.file(sourceFile.getName()));
        }
    }
}
