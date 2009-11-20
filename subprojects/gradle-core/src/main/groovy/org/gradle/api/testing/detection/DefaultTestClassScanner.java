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

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.internal.file.FileSet;
import org.gradle.api.testing.fabric.TestFrameworkDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The default test class scanner depending on the availability of a test framework detecter a detection or filename
 * scan is performed to find test classes.
 *
 * @author Tom Eyckmans
 */
public class DefaultTestClassScanner implements TestClassScanner {
    private final File testClassDirectory;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final TestFrameworkDetector testFrameworkDetector;
    private final TestClassProcessor testClassProcessor;

    public DefaultTestClassScanner(File testClassDirectory, Set<String> includePatterns, Set<String> excludePatterns,
                                   TestFrameworkDetector testFrameworkDetector, TestClassProcessor testClassProcessor) {
        if (testClassDirectory == null) {
            throw new IllegalArgumentException("testClassDirectory is null!");
        }
        if (testClassProcessor == null) {
            throw new IllegalArgumentException("testClassProcessor is null!");
        }

        this.testClassDirectory = testClassDirectory;
        this.includePatterns = includePatterns == null ? new ArrayList<String>() : new ArrayList<String>(
                includePatterns);
        this.excludePatterns = excludePatterns == null ? new ArrayList<String>() : new ArrayList<String>(
                excludePatterns);
        this.testFrameworkDetector = testFrameworkDetector;
        this.testClassProcessor = testClassProcessor;
    }

    public void executeScan() {
        final FileSet testClassFileSet = new FileSet(testClassDirectory, null);

        if (testFrameworkDetector == null) {
            filenameScan(testClassFileSet);
        } else {
            detectionScan(testClassFileSet);
        }
    }

    private void detectionScan(final FileSet testClassFileSet) {
        testClassFileSet.include(includePatterns);
        testClassFileSet.exclude(excludePatterns);

        testFrameworkDetector.setTestClassProcessor(testClassProcessor);

        testClassFileSet.visit(new ClassFileVisitor() {
            public void visitClassFile(FileVisitDetails fileDetails) {
                testFrameworkDetector.processTestClass(fileDetails.getFile());
            }
        });
    }

    private void filenameScan(final FileSet testClassFileSet) {
        if (includePatterns.isEmpty()) {
            includePatterns.add("**/*Tests.class");
            includePatterns.add("**/*Test.class");
        }
        if (excludePatterns.isEmpty()) {
            excludePatterns.add("**/Abstract*.class");
        }
        testClassFileSet.include(includePatterns);
        testClassFileSet.exclude(excludePatterns);

        testClassFileSet.visit(new ClassFileVisitor() {
            public void visitClassFile(FileVisitDetails fileDetails) {
                testClassProcessor.processTestClass(fileDetails.getRelativePath().getPathString().replaceAll("\\.class",
                        ""));
            }
        });
    }

    private abstract class ClassFileVisitor extends EmptyFileVisitor {
        public void visitFile(FileVisitDetails fileDetails) {
            final File file = fileDetails.getFile();

            if (file.getAbsolutePath().endsWith(".class")) {
                visitClassFile(fileDetails);
            }
        }

        public abstract void visitClassFile(FileVisitDetails fileDetails);
    }
}
