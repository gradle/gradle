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

package org.gradle.api.testing.detection;

import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.FileVisitDetails;

import java.util.*;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class TestClassScanner {
    private final File testClassDirectory;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final TestFrameworkInstance testFrameworkInstance;
    private final boolean scanForTestClasses;

    public TestClassScanner(File testClassDirectory, Collection<String> includePatterns, Collection<String> excludePatterns, TestFrameworkInstance testFrameworkInstance, boolean scanForTestClasses) {
        this.testClassDirectory = testClassDirectory;
        this.includePatterns = includePatterns == null ? new ArrayList<String>() : new ArrayList<String>(includePatterns);
        this.excludePatterns = excludePatterns == null ? new ArrayList<String>() : new ArrayList<String>(excludePatterns);
        this.testFrameworkInstance = testFrameworkInstance;
        this.scanForTestClasses = scanForTestClasses;
    }

    public void getTestClassNames() {
        final FileSet testClassFileSet = new FileSet(testClassDirectory, null);

        if (!scanForTestClasses) {
            if (includePatterns.isEmpty()) {
                includePatterns.add("**/*Tests.class");
                includePatterns.add("**/*Test.class");
            }
            if (excludePatterns.isEmpty()) {
                excludePatterns.add("**/Abstract*.class");
            }
        }
        testClassFileSet.include(includePatterns);
        testClassFileSet.exclude(excludePatterns);

        testClassFileSet.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                File file = fileDetails.getFile();
                if (file.getAbsolutePath().endsWith(".class")) {
                    final String fileResourceName = fileDetails.getRelativePath().getPathString();

                    if (scanForTestClasses) {
                        testFrameworkInstance.processPossibleTestClass(file);
                    } else {
                        testFrameworkInstance.manualTestClass(fileResourceName);
                    }
                }
            }
        });
    }
}
