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

package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/38623")
class ToolingApiNonProjectDirectoryIntegrationTest extends AbstractIntegrationSpec {

    private static final String NOT_PART_OF_THE_BUILD = "is not part of the build defined by settings file"

    final GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    final ToolingApi toolingApi = new ToolingApi(dist, temporaryFolder)

    TestFile nested

    def setup() {
        settingsFile << "rootProject.name = 'outer'"
        nested = file("nested")
        nested.file("build.gradle") << """
            tasks.register('hello') { doLast { println('hello') } }
        """
        toolingApi.withConnector { connector -> connector.forProjectDirectory(nested) }
    }

    def "fetching a model for a directory that is not part of the surrounding build fails"() {
        expect:
        outcomeOf { it.getModel(GradleProject) }.contains(NOT_PART_OF_THE_BUILD)
    }

    def "fetching a model for a directory that is not part of the surrounding build fails with the build cache enabled"() {
        expect:
        outcomeOf { it.model(GradleProject).withArguments("--build-cache").get() }.contains(NOT_PART_OF_THE_BUILD)
    }

    def "running a task in a directory that is not part of the surrounding build fails"() {
        expect:
        outcomeOf { it.newBuild().forTasks("hello").run() }.contains(NOT_PART_OF_THE_BUILD)
    }

    private String outcomeOf(Closure<?> action) {
        try {
            def result = toolingApi.withConnection { ProjectConnection connection -> action(connection) }
            return result instanceof GradleProject
                ? "succeeded, project '${result.name}' at '${result.projectDirectory.name}'"
                : "succeeded"
        } catch (GradleConnectionException e) {
            def messages = []
            for (Throwable t = e; t != null; t = t.cause) {
                messages << t.message
            }
            return "failed: " + messages.join(" | ")
        }
    }
}
