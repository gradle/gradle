/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.IsEmbeddedExecutor)
class TaskValidationFailureRenderingIntegrationTest extends AbstractIntegrationSpec {

    private String docLink() {
        "https://docs.gradle.org/${distribution.version.version}/userguide/id.html#section"
    }

    def "renders single error problem in failure block"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @org.gradle.integtests.fixtures.validation.ValidationProblem(fatal = true) String foo
                @TaskAction void run() {}
            }

            tasks.register("brokenRun", MyTask)
        """

        when:
        fails "brokenRun"

        then:
        failure.assertHasErrorOutput("""* What went wrong:
A problem was found with the configuration of task ':brokenRun' (type 'MyTask').
test problem
  Type 'MyTask' property 'foo' test problem
    This is a test.
    For more information, please refer to ${docLink()} in the Gradle documentation.""")
    }

    def "renders multiple error problems in failure block"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @org.gradle.integtests.fixtures.validation.ValidationProblem(fatal = true) String foo
                @org.gradle.integtests.fixtures.validation.ValidationProblem(fatal = true) String bar
                @TaskAction void run() {}
            }

            tasks.register("brokenRun", MyTask)
        """

        when:
        fails "brokenRun"

        then:
        failure.assertHasErrorOutput("""* What went wrong:
Some problems were found with the configuration of task ':brokenRun' (type 'MyTask').
test problem
  Type 'MyTask' property 'bar' test problem
    This is a test.
    For more information, please refer to ${docLink()} in the Gradle documentation.
test problem
  Type 'MyTask' property 'foo' test problem
    This is a test.
    For more information, please refer to ${docLink()} in the Gradle documentation.""")
    }

    def "renders only errors in failure block when warning and error are mixed"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @org.gradle.integtests.fixtures.validation.ValidationProblem(fatal = true) String foo
                @org.gradle.integtests.fixtures.validation.ValidationProblem String bar
                @TaskAction void run() {}
            }

            tasks.register("brokenRun", MyTask)
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Type 'MyTask' property 'bar' test problem. Reason: This is a test. " +
                "This behavior has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Execution optimizations are disabled to ensure correctness. " +
                "For more information, please refer to ${docLink()} in the Gradle documentation."
        )
        fails "brokenRun"

        then:
        failure.assertHasErrorOutput("""* What went wrong:
A problem was found with the configuration of task ':brokenRun' (type 'MyTask').
test problem
  Type 'MyTask' property 'foo' test problem
    This is a test.
    For more information, please refer to ${docLink()} in the Gradle documentation.""")
    }

    def "renders warnings inline when build succeeds"() {
        buildFile << """
            class MyTask extends DefaultTask {
                @org.gradle.integtests.fixtures.validation.ValidationProblem String bar
                @TaskAction void run() {}
            }

            tasks.register("warnRun", MyTask)
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Type 'MyTask' property 'bar' test problem. Reason: This is a test. " +
                "This behavior has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Execution optimizations are disabled to ensure correctness. " +
                "For more information, please refer to ${docLink()} in the Gradle documentation."
        )

        then:
        succeeds "warnRun"

        and:
        outputContains("""Problem found: test problem (id: root:test-problem)
  Type 'MyTask' property 'bar' test problem
    This is a test.
    For more information, please refer to ${docLink()} in the Gradle documentation.""")
    }
}
