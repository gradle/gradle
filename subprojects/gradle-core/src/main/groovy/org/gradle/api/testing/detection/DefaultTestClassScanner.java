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
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.testing.fabric.TestFrameworkDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Tom Eyckmans
 */
public class DefaultTestClassScanner implements TestClassScanner {
    private final File testClassDirectory;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final TestFrameworkDetector testFrameworkDetector;
    private final TestClassProcessor testClassProcessor;
    private final boolean scanForTestClasses;

    public DefaultTestClassScanner(AbstractTestTask testTask, TestFrameworkDetector testFrameworkDetector, TestClassProcessor testClassProcessor) {
        this.testClassDirectory = testTask.getTestClassesDir();
        final Set<String> includePatterns = testTask.getIncludes();
        final Set<String> excludePatterns = testTask.getExcludes();
        this.includePatterns = includePatterns == null ? new ArrayList<String>() : new ArrayList<String>(includePatterns);
        this.excludePatterns = excludePatterns == null ? new ArrayList<String>() : new ArrayList<String>(excludePatterns);
        this.testFrameworkDetector = testFrameworkDetector;
        this.testClassProcessor = testClassProcessor;
        this.scanForTestClasses = testTask.isScanForTestClasses();
    }

    public void executeScan() {
        testFrameworkDetector.setTestClassProcessor(testClassProcessor);

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

        testClassFileSet.visit(new EmptyFileVisitor() {
            public void visitFile(FileVisitDetails fileDetails) {
                final File file = fileDetails.getFile();
                
                if (file.getAbsolutePath().endsWith(".class")) {
                    testFrameworkDetector.processTestClass(file);
                }
            }
        });
    }
}
