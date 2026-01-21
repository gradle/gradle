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

package org.gradle.integtests.tooling.r950

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion(">=9.5.0")
@TargetGradleVersion(">=9.4.0") // the required provider-side features were added in 9.4.0
class VersionConsumerCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=4.0 <9.4.0")
    def "version request is ignored for old Gradle version"() {
        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("projects")
                .withArguments("--version")
                .run()
        }

        then:
        assertSuccessful()
        result.assertHasErrorOutput('The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.')
        result.assertTaskExecuted(":projects")
    }

    def "prints version and ignores tasks when --version is present"() {
        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("invalidTask")
                .withArguments(arg)
                .run()
        }

        then:
        assertVersionInfoRendered(result)
        result.assertNotOutput("BUILD") // no BUILD SUCCESSFUL or BUILD FAILED message at the end

        where:
        arg << ['--version', '-v']
    }

    def "requesting version via #entryPoint is ignored"() {
        setup:
        buildFile << """
            plugins { id 'java-library' }
            ${mavenCentralRepository()}
            dependencies { testImplementation("junit:junit:4.13.2") }
        """
        file('src/test/java/MyTest.java') << """
            public class MyTest {
                @org.junit.Test public void testSomething() {
                    org.junit.Assert.assertTrue(true);
                }
            }
        """

        when:
        withConnection { connection ->  entryPointConfig(connection) }

        then:
        assertSuccessful()
        result.assertHasErrorOutput('The Tooling API does not support --help, --version or --show-version arguments for this operation. These arguments have been ignored.')

        where:
        entryPoint      | entryPointConfig
        "test launcher" | { ProjectConnection conn -> conn.newTestLauncher().withJvmTestClasses("MyTest").withArguments('--version').run() }
        "model builder" | { ProjectConnection conn -> conn.model(GradleProject) .withArguments('--version').get() }
        "build action"  | { ProjectConnection conn -> conn.action(new GetGradleProjectAction()).withArguments('--version').run() }
    }

    static class GetGradleProjectAction implements BuildAction<GradleProject> {
        GradleProject execute(BuildController controller) {
            return controller.getModel(GradleProject)
        }
    }

    def "prints version and runs tasks when --show-version is present"() {
        when:
        withConnection { connection ->
            connection.newBuild()
                .forTasks("help")
                .withArguments("--show-version")
                .run()
        }

        then:
        assertSuccessful()
        assertVersionInfoRendered(result)

        where:
        arg << ['--show-version', '-V']
    }

    private static void assertVersionInfoRendered(ExecutionResult result) {
        result.assertOutputContains('Gradle')
        result.assertOutputContains('Build time:')
        result.assertOutputContains('Revision:')
    }
}
