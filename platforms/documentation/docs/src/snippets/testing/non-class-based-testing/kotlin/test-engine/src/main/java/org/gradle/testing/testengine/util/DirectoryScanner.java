/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.testengine.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class DirectoryScanner {
    private TestFileParser testFileParser = new TestFileParser();

    public TestFileParser getTestFileParser() {
        return testFileParser;
    }

    public List<File> scanDirectory(File dir) {
        return scanDirectory(dir, false);
    }

    public List<File> scanDirectory(File dir, boolean includeDirs) {
        List<File> filesFound = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (includeDirs) {
                        filesFound.add(file);
                    }
                    filesFound.addAll(scanDirectory(file));
                } else if (testFileParser.isValidTestDefinitionFile(file)) {
                    filesFound.add(file);
                }
            }
        }
        return filesFound;
    }
}
