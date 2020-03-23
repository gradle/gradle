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
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;

import java.util.regex.Pattern;

/**
 * The default test class scanner. Depending on the availability of a test framework detector,
 * a detection or filename scan is performed to find test classes.
 */
public class DefaultTestClassScanner implements Runnable {
    private static final Pattern ANONYMOUS_CLASS_NAME = Pattern.compile(".*\\$\\d+");
    private final FileTree candidateClassFiles;
    private final TestFrameworkDetector testFrameworkDetector;
    private final TestClassProcessor testClassProcessor;

    public DefaultTestClassScanner(FileTree candidateClassFiles, TestFrameworkDetector testFrameworkDetector,
                                   TestClassProcessor testClassProcessor) {
        this.candidateClassFiles = candidateClassFiles;
        this.testFrameworkDetector = testFrameworkDetector;
        this.testClassProcessor = testClassProcessor;
    }

    @Override
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
            @Override
            public void visitClassFile(FileVisitDetails fileDetails) {
                testFrameworkDetector.processTestClass(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
            }
        });
    }

    private void filenameScan() {
        candidateClassFiles.visit(new ClassFileVisitor() {
            @Override
            public void visitClassFile(FileVisitDetails fileDetails) {
                TestClassRunInfo testClass = new DefaultTestClassRunInfo(getClassName(fileDetails));
                testClassProcessor.processTestClass(testClass);
            }
        });
    }

    private abstract class ClassFileVisitor extends EmptyFileVisitor {
        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            if (isClass(fileDetails) && !isAnonymousClass(fileDetails)) {
                visitClassFile(fileDetails);
            }
        }

        abstract void visitClassFile(FileVisitDetails fileDetails);

        private boolean isAnonymousClass(FileVisitDetails fileVisitDetails) {
            return ANONYMOUS_CLASS_NAME.matcher(getClassName(fileVisitDetails)).matches();
        }

        private boolean isClass(FileVisitDetails fileVisitDetails) {
            String fileName = fileVisitDetails.getFile().getName();
            return fileName.endsWith(".class") && !"module-info.class".equals(fileName);
        }
    }

    private String getClassName(FileVisitDetails fileDetails) {
        return fileDetails.getRelativePath().getPathString().replaceAll("\\.class", "").replace('/', '.');
    }
}
