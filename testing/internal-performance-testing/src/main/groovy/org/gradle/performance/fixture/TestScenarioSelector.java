/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.fixture;

import com.google.common.base.Splitter;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Determines whether a specific scenario within a performance test should run.
 *
 * This is used as a workaround for not being able to add a test filter for unrolled Spock tests.
 */
public class TestScenarioSelector {
    private static final String TEST_PROJECT_PROPERTY_NAME = "org.gradle.performance.testProject";

    public static boolean shouldRun(String testId) {
        if (testId.contains(";")) {
            throw new IllegalArgumentException("Test ID cannot contain ';', but was '" + testId + "'");
        }
        String scenarioProperty = System.getProperty("org.gradle.performance.scenarios", "");
        List<String> scenarios = Splitter.on(";").omitEmptyStrings().trimResults().splitToList(scenarioProperty);

        return scenarios.isEmpty() || scenarios.contains(testId);
    }

    @Nullable
    static String loadConfiguredTestProject() {
        String testProjectFromSystemProperty = System.getProperty(TEST_PROJECT_PROPERTY_NAME);
        return (testProjectFromSystemProperty != null && !testProjectFromSystemProperty.isEmpty()) ? testProjectFromSystemProperty : null;
    }
}
