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
package org.gradle.api.testing;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.testing.fabric.TestFramework;
import org.gradle.external.junit.JUnitTestFramework;
import org.gradle.external.testng.TestNGTestFramework;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a map<TestFramework.id, TestFramework> of all the supported TestFrameworks.
 *
 * The default supported Test frameworks are: - JUnit (id = "junit") - TestNG (id = "testng")
 *
 * @author Tom Eyckmans
 */
public class TestFrameworkRegister {
    private static final Map<String, TestFramework> TEST_FRAMEWORKS = new ConcurrentHashMap<String, TestFramework>();

    static {
        // add the default supported test frameworks (JUnit, TestNG)
        final TestFramework junit = new JUnitTestFramework();
        final TestFramework testng = new TestNGTestFramework();

        registerTestFramework(junit);
        registerTestFramework(testng);
    }

    /**
     * Register an additional test framework.
     *
     * In case the test framework id is already used the new test framework replaces the old registered one.
     *
     * @param testFramework The test framework to register.
     * @return The previously registered test framework for the same test framework id.
     * @throws IllegalArgumentException when the test framework is null or the id of the test framework is empty
     */
    public static TestFramework registerTestFramework(final TestFramework testFramework) {
        if (testFramework == null) {
            throw new IllegalArgumentException("testFramework == null!");
        }

        final String testFrameworkId = testFramework.getId();

        if (StringUtils.isEmpty(testFrameworkId)) {
            throw new IllegalArgumentException("testFramework.id is empty!");
        }

        return TEST_FRAMEWORKS.put(testFrameworkId, testFramework);
    }

    public static TestFramework getTestFramework(final String testFrameworkId) {
        if (StringUtils.isEmpty(testFrameworkId)) {
            throw new IllegalArgumentException("testFrameworkId is empty!");
        }

        return TEST_FRAMEWORKS.get(testFrameworkId);
    }
}
