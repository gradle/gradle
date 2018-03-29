/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.console.taskgrouping


abstract class AbstractFailureReportingFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    def "reports build failure at the end of the build"() {
        buildFile << """
            task broken {
                doLast {
                    throw new RuntimeException("broken")
                }
            }
        """

        expect:
        fails("broken")

        and:
        // Ensure the failure is a location that the fixtures can see
        failure.assertHasDescription("Execution failed for task ':broken'")
        failure.assertHasCause("broken")

        // Check that the failure text appears in stdout and not stderr
        failure.output.contains("Build failed with an exception.")
        failure.output.contains("""
            * What went wrong:
            Execution failed for task ':broken'.
        """.stripIndent().trim())

        !failure.error.contains("Build failed with an exception.")
    }
}
