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
package org.gradle.api.testing.detection;

import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestFrameworkDetector;

import java.util.Set;
import java.io.File;

/**
 * The default test class scanner factory.
 *
 * @author Tom Eyckmans
 */
public class DefaultTestClassScannerFactory implements TestClassScannerFactory {
    public TestClassScanner createTestClassScanner(AbstractTestTask testTask, TestClassProcessor testClassProcessor) {
        final File testClassDirectory = testTask.getTestClassesDir();
        final Set<String> includePatterns = testTask.getIncludes();
        final Set<String> excludePatterns = testTask.getExcludes();

        if (testTask.isScanForTestClasses()) {
            final TestFrameworkDetector testFrameworkDetector = testTask.getTestFramework().getDetector();

            return new DefaultTestClassScanner(testClassDirectory, includePatterns, excludePatterns,
                    testFrameworkDetector, testClassProcessor);
        } else {
            return new DefaultTestClassScanner(testClassDirectory, includePatterns, excludePatterns, null,
                    testClassProcessor);
        }
    }
}
