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

package org.gradle.integtests.tooling.r50

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r33.FetchIdeaProject

@TargetGradleVersion(">=5.0")
@ToolingApiVersion('>=4.4')
class ProjectStateLockingCrossVersionTest extends ToolingApiSpecification {
    def "does not emit deprecation warnings when idea model builder resolves a configuration"() {
        singleProjectBuildInRootFolder("root") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        when:
        withConnection {
            def executer = action(new FetchIdeaProject())
            executer.withArguments("--warning-mode", "all")
            collectOutputs(executer)
            executer.run()
        }

        then:
        !stdout.toString().contains("was resolved without accessing the project in a safe manner")

        and:
        assertHasConfigureSuccessfulLogging()
    }

    def "does not emit deprecation warnings when eclipse model builder resolves a configuration"() {
        singleProjectBuildInRootFolder("root") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        when:
        withConnection {
            def executer = action(new FetchEclipseProject())
            executer.withArguments("--warning-mode", "all")
            collectOutputs(executer)
            executer.run()
        }

        then:
        !stdout.toString().contains("was resolved without accessing the project in a safe manner")

        and:
        assertHasConfigureSuccessfulLogging()
    }
}
