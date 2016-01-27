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

package org.gradle.api.internal.tasks.testing.detection;

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;

import java.io.File;

/**
 * The default test class scanner. Depending on the availability of a test framework detector,
 * a detection or filename scan is performed to find test classes.
 */
public class DefaultTestClassScanner implements Runnable {
    private final FileTree candidateClassFiles;
    private final TestFrameworkDetector testFrameworkDetector;
    private final TestClassProcessor testClassProcessor;

    public DefaultTestClassScanner(FileTree candidateClassFiles, TestFrameworkDetector testFrameworkDetector,
                                   TestClassProcessor testClassProcessor) {
        this.candidateClassFiles = candidateClassFiles;
        this.testFrameworkDetector = testFrameworkDetector;
        this.testClassProcessor = testClassProcessor;
    }

    public void run() {
        if (testFrameworkDetector == null) {
            filenameScan();
        } else {
            detectionScan();
        }
    }

    private void detectionScan() {
        testFrameworkDetector.startDetection(testClassProcessor);
        candidateClassFiles.visit(new ClassFileVisitor() {
            public void visitClassFile(FileVisitDetails fileDetails) {
                testFrameworkDetector.processTestClass(fileDetails.getFile());
            }
        });
    }

    private void filenameScan() {
        candidateClassFiles.visit(new ClassFileVisitor() {
            public void visitClassFile(FileVisitDetails fileDetails) {
                String className = fileDetails.getRelativePath().getPathString().replaceAll("\\.class", "").replace('/', '.');
                TestClassRunInfo testClass = new DefaultTestClassRunInfo(className);
                testClassProcessor.processTestClass(testClass);
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
