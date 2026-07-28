/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer.framework;

import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * Instances are scoped to a test task execution and are reused between rounds.
 */
public interface TestFrameworkStrategy {

    Pattern SPOCK2_CORE_JAR_NAME_PATTERN = Pattern.compile(".*/spock-core-2[^/]*\\.jar");

    @Nullable
    static TestFrameworkStrategy of(JvmTestExecutionSpec spec) {
        TestFramework testFramework = spec.getTestFramework();
        if (testFramework instanceof JUnitTestFramework) {
            return new JunitTestFrameworkStrategy();
        } else if (testFramework instanceof JUnitPlatformTestFramework) {
            return new Junit5TestFrameworkStrategy(isSpock2Used(spec));
        } else if (testFramework instanceof TestNGTestFramework) {
            return new TestNgTestFrameworkStrategy();
        } else {
            return null;
        }
    }

    static boolean isSpock2Used(JvmTestExecutionSpec spec) {
        return isSpock2JarOnPath(spec.getClasspath()) || supportsJavaModules() && isSpock2JarOnPath(spec.getModulePath());
    }

    static boolean supportsJavaModules() {
        return gradleVersionIsAtLeast("6.4");
    }

    static boolean isSpock2JarOnPath(Iterable<? extends File> path) {
        return StreamSupport.stream(path.spliterator(), false)
            .anyMatch(TestFrameworkStrategy::isSpock2CoreJar);
    }

    static boolean isSpock2CoreJar(File file) {
        return SPOCK2_CORE_JAR_NAME_PATTERN.matcher(file.getAbsolutePath()).matches();
    }

    static boolean gradleVersionIsAtLeast(String version) {
        return GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version(version)) >= 0;
    }

    boolean isLifecycleFailureTest(TestsReader testsReader, String className, String testName);

    TestFramework createRetrying(TestFrameworkTemplate template, TestFramework testFramework, TestNames failedTests, Set<String> testClassesSeenInCurrentRound);

    default boolean isExpectedUnretriedTest(String className, String test) {
        return false;
    }
}
