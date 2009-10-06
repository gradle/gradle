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
 * @author Tom Eyckmans
 */
public class TestFrameworkRegister {
    private static final Map<String, TestFramework> testFrameworks = new ConcurrentHashMap<String, TestFramework>();

    static {
        final TestFramework junit = new JUnitTestFramework();
        final TestFramework testng = new TestNGTestFramework();

        registerTestFramework(junit);
        registerTestFramework(testng);
    }

    public static void registerTestFramework(final TestFramework testFramework) {
        if (testFramework == null) throw new IllegalArgumentException("testFramework == null!");

        final String testFrameworkId = testFramework.getId();

        if (testFrameworks.containsKey(testFrameworkId))
            throw new IllegalArgumentException("testFramework (" + testFrameworkId + ") already registered!");

        testFrameworks.put(testFrameworkId, testFramework);
    }

    public static TestFramework getTestFramework(final String testFrameworkId) {
        if (StringUtils.isEmpty(testFrameworkId)) throw new IllegalArgumentException("testFrameworkId is empty!");

        return testFrameworks.get(testFrameworkId);
    }
}
