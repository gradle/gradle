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
package org.gradle.testretry.testframework

import org.gradle.api.internal.tasks.testing.TestFramework
import org.gradle.testretry.AbstractFrameworkFuncTest

class UnsupportedTestFrameworkFuncTest extends AbstractFrameworkFuncTest {

    private static String newJUnitTestFrameworkInstance() {
        """
            testTask.objectFactory.newInstance(
               org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework.class,
               testFilter,
               new org.gradle.internal.Factory<java.io.File>() {
                    @Override
                    public java.io.File create() {
                        return new java.io.File("some/unused/temp/dir");
                    }
               },
               org.gradle.api.internal.provider.Providers.TRUE
            )
        """
    }

    def "logs warning if test framework is unsupported"() {
        given:
        buildFile << """
            test.retry.maxRetries = 2

            class CustomTestFramework implements $TestFramework.name {
                @Delegate
                private final $TestFramework.name delegate

                CustomTestFramework(org.gradle.api.tasks.testing.Test testTask) {
                    def testFilter = new org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter()
                    this.delegate = ${newJUnitTestFrameworkInstance()}
                }
            }
            test.useTestFramework(new CustomTestFramework(test))
        """

        successfulTest()

        when:
        succeeds('test')

        then:
        output.contains("Test retry requested for task :test with unsupported test framework CustomTestFramework - failing tests will not be retried\n")
    }
}
