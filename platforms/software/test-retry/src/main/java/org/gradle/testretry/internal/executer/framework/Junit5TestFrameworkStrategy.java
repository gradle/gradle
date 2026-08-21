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

import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;
import org.gradle.testretry.internal.executer.framework.TestFrameworkProvider.ProviderForCurrentGradleVersion;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.gradle.testretry.internal.executer.framework.Junit5TestFrameworkStrategy.Junit5TestFrameworkProvider.testFrameworkProvider;
import static org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy.gradleVersionIsAtLeast;

final class Junit5TestFrameworkStrategy extends BaseJunitTestFrameworkStrategy {

    private final boolean isSpock2Used;

    public Junit5TestFrameworkStrategy(boolean isSpock2Used) {
        this.isSpock2Used = isSpock2Used;
    }

    @Override
    public TestFramework createRetrying(TestFrameworkTemplate template, TestFramework testFramework, TestNames failedTests, Set<String> testClassesSeenInCurrentRound) {
        DefaultTestFilter failedTestsFilter = testFilterFor(failedTests, isSpock2Used, template, testClassesSeenInCurrentRound);
        return testFrameworkProvider(template, testFramework).testFrameworkFor(failedTestsFilter);
    }

    static class Junit5TestFrameworkProvider {

        static class ProviderForGradleOlderThanV8 implements TestFrameworkProvider {

            private final TestFrameworkTemplate template;

            ProviderForGradleOlderThanV8(TestFrameworkTemplate template) {
                this.template = template;
            }

            @Override
            public TestFramework testFrameworkFor(DefaultTestFilter failedTestsFilter) {
                JUnitPlatformTestFramework retryTestFramework = newInstance(failedTestsFilter);
                copyOptions((JUnitPlatformOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

                return retryTestFramework;
            }

            private static JUnitPlatformTestFramework newInstance(DefaultTestFilter failedTestsFilter) {
                try {
                    Class<?> jUnitPlatformTestFrameworkClass = JUnitPlatformTestFramework.class;
                    Constructor<?> constructor = jUnitPlatformTestFrameworkClass.getConstructor(DefaultTestFilter.class);

                    return (JUnitPlatformTestFramework) constructor.newInstance(failedTestsFilter);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            private static void copyOptions(JUnitPlatformOptions source, JUnitPlatformOptions target) {
                target.setIncludeEngines(source.getIncludeEngines());
                target.setExcludeEngines(source.getExcludeEngines());
                target.setIncludeTags(source.getIncludeTags());
                target.setExcludeTags(source.getExcludeTags());
            }
        }

        static TestFrameworkProvider testFrameworkProvider(TestFrameworkTemplate template, TestFramework testFramework) {
            if (gradleVersionIsAtLeast("8.0")) {
                return new ProviderForCurrentGradleVersion(testFramework);
            } else {
                return new ProviderForGradleOlderThanV8(template);
            }
        }

    }

}
