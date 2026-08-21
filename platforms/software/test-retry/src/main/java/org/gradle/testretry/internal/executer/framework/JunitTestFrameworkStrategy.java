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
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.testretry.internal.executer.TestFrameworkTemplate;
import org.gradle.testretry.internal.executer.TestNames;

import java.lang.reflect.Constructor;
import java.util.Set;

import static org.gradle.testretry.internal.executer.framework.JunitTestFrameworkStrategy.JunitTestFrameworkProvider.testFrameworkProvider;
import static org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy.gradleVersionIsAtLeast;

final class JunitTestFrameworkStrategy extends BaseJunitTestFrameworkStrategy implements TestFrameworkStrategy {

    @Override
    public TestFramework createRetrying(TestFrameworkTemplate template, TestFramework testFramework, TestNames failedTests, Set<String> testClassesSeenInCurrentRound) {
        DefaultTestFilter failedTestsFilter = testFilterFor(failedTests, true, template, testClassesSeenInCurrentRound);
        return testFrameworkProvider(template, testFramework).testFrameworkFor(failedTestsFilter);
    }

    static class JunitTestFrameworkProvider {

        static class ProviderForGradleOlderThanV8 implements TestFrameworkProvider {

            private final TestFrameworkTemplate template;

            ProviderForGradleOlderThanV8(TestFrameworkTemplate template) {
                this.template = template;
            }

            @Override
            public TestFramework testFrameworkFor(DefaultTestFilter failedTestsFilter) {
                JUnitTestFramework retryTestFramework = newInstance(template.task, failedTestsFilter);
                copyOptions((JUnitOptions) template.task.getTestFramework().getOptions(), retryTestFramework.getOptions());

                return retryTestFramework;
            }

            private static JUnitTestFramework newInstance(Test task, DefaultTestFilter failedTestsFilter) {
                try {
                    Class<?> jUnitTestFrameworkClass = JUnitTestFramework.class;
                    Constructor<?> constructor = jUnitTestFrameworkClass.getConstructor(Test.class, DefaultTestFilter.class);

                    return (JUnitTestFramework) constructor.newInstance(task, failedTestsFilter);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }

            private static void copyOptions(JUnitOptions source, JUnitOptions target) {
                target.setIncludeCategories(source.getIncludeCategories());
                target.setExcludeCategories(source.getExcludeCategories());
            }
        }

        static TestFrameworkProvider testFrameworkProvider(TestFrameworkTemplate template, TestFramework testFramework) {
            if (gradleVersionIsAtLeast("8.0")) {
                return new TestFrameworkProvider.ProviderForCurrentGradleVersion(testFramework);
            } else {
                return new ProviderForGradleOlderThanV8(template);
            }
        }

    }

}
