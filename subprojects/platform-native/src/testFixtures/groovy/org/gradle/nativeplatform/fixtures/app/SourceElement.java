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
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
     * Returns the source set name to write the source into, using the Gradle convention for source layout.
     */
    public String getSourceSetName() {
        return "main";
    }

    /**
     * Writes the source files of this element to the given project, using the Gradle convention for source layout.
     */
    public void writeToProject(TestFile projectDir) {
        TestFile srcDir = projectDir.file("src/" + getSourceSetName());
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

    public static SourceElement empty() {
        return new SourceElement() {
            @Override
            public List<SourceFile> getFiles() {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Returns a source element that contains the union of the given elements.
     */
    public static SourceElement ofElements(final SourceElement... elements) {
        return new SourceElement() {
            @Override
            public List<SourceFile> getFiles() {
                List<SourceFile> files = new ArrayList<SourceFile>();
                for (SourceElement element : elements) {
                    files.addAll(element.getFiles());
                }
                return files;
            }
        };
    }

    /**
     * Returns a source element that contains the given files
     */
    public static SourceElement ofFiles(final SourceFile... files) {
        return new SourceElement() {
            @Override
            public List<SourceFile> getFiles() {
                return Arrays.asList(files);
            }
        };
    }

    /**
     * Returns a source element that contains the given files
     */
    public static SourceElement ofFiles(final List<SourceFile> files) {
        return new SourceElement() {
            @Override
            public List<SourceFile> getFiles() {
                return files;
            }
        };
    }

    public List<String> getSourceFileNames() {
        return CollectionUtils.collect(getFiles(), SourceFile::getName);
    }
}
