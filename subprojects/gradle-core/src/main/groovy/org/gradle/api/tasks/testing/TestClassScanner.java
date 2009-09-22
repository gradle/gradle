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

package org.gradle.api.tasks.testing;

import org.gradle.api.testing.TestFramework;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.*;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class TestClassScanner {
    private static final Logger logger = LoggerFactory.getLogger(TestClassScanner.class);
    private final File testClassDirectory;
    private final List<String> includePatterns;
    private final List<String> excludePatterns;
    private final TestFramework testFramework;
    private final boolean scanForTestClasses;

    public TestClassScanner(File testClassDirectory, Collection<String> includePatterns,
                            Collection<String> excludePatterns, TestFramework testFramework,
                            boolean scanForTestClasses) {
        this.testClassDirectory = testClassDirectory;
        this.includePatterns = includePatterns == null ? new ArrayList<String>() : new ArrayList<String>(
                includePatterns);
        this.excludePatterns = excludePatterns == null ? new ArrayList<String>() : new ArrayList<String>(
                excludePatterns);
        this.testFramework = testFramework;
        this.scanForTestClasses = scanForTestClasses;
    }

    public Set<String> getTestClassNames() {
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

        final Set<String> testClassNames = new HashSet<String>();
        testClassFileSet.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
            }

            public void visitFile(FileVisitDetails fileDetails) {
                File file = fileDetails.getFile();
                if (file.getAbsolutePath().endsWith(".class")) {
                    final String fileResourceName = fileDetails.getRelativePath().getPathString();
                    logger.debug("test-class-scan : scanning {}", fileResourceName);
                    if (scanForTestClasses) {
                        if (!testFramework.isTestClass(file)) {
                            logger.debug("test-class-scan : discarded {} not a test class", fileResourceName);
                        }
                    } else {
                        testClassNames.add(fileResourceName);
                    }
                }
            }
        });

        if (scanForTestClasses) {
            return testFramework.getTestClassNames();
        } else {
            return testClassNames;
        }
    }
}
