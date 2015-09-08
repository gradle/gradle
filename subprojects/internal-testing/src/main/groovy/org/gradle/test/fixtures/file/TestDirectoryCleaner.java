/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.test.fixtures.file;

import org.apache.commons.io.FileUtils;
import org.junit.runner.Description;

public class TestDirectoryCleaner {
    private final TestFile testDirectory;
    private final boolean leaksHandles;
    private final String displayName;
    private final boolean skipCleanup;

    public TestDirectoryCleaner(TestFile testDirectory, Class<?> testClass, Description description) {
        this(testDirectory, testClass, description, false);
    }

    public TestDirectoryCleaner(TestFile testDirectory, Class<?> testClass, Description description, boolean skipIfCleanupAnnotated) {
        this.testDirectory = testDirectory;
        this.leaksHandles =
            testClass.getAnnotation(LeaksFileHandles.class) != null
                || description.getAnnotation(LeaksFileHandles.class) != null
                // For now, assume that all tests run with the daemon executer leak file handles
                // This seems to be true for any test that uses `GradleExecuter.requireOwnGradleUserHomeDir`
                || "daemon".equals(System.getProperty("org.gradle.integtest.executer"));
        this.displayName = description.getDisplayName();

        boolean cleansUpWithInterceptor =
            testClass.getAnnotation(CleanupTestDirectory.class) != null
                || description.getAnnotation(CleanupTestDirectory.class) != null;

        this.skipCleanup = cleansUpWithInterceptor && skipIfCleanupAnnotated;
    }

    public void cleanup() throws Throwable {
        if (skipCleanup) {
            return;
        }

        try {
            if (testDirectory.exists()) {
                FileUtils.forceDelete(testDirectory);
            }
        } catch (Exception e) {
            if (leaksHandles) {
                System.err.println("Couldn't delete test dir for " + displayName + " (test is holding files open)");
                e.printStackTrace(System.err);
            } else {
                throw e;
            }
        }
    }
}
