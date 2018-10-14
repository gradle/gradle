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
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r33.FetchIdeaProject

@TargetGradleVersion(">=5.0")
@ToolingApiVersion('>=4.4')
class ProjectStateLockingCrossVersionTest extends ToolingApiSpecification {
    def "does not emit deprecation warnings when idea model builder resolves a configuration"() {
        def stdOut = new TestOutputStream()
        def stdErr = new TestOutputStream()
        singleProjectBuildInRootFolder("root") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        when:
        withConnection {
            action(new FetchIdeaProject()).withArguments("--warning-mode", "all").setStandardOutput(stdOut).setStandardError(stdErr).run()
        }

        then:
        println stdOut.toString()
        !stdOut.toString().contains("was resolved without accessing the project in a safe manner")

        and:
        stdOut.toString().contains("CONFIGURE SUCCESSFUL")
    }

    def "does not emit deprecation warnings when eclipse model builder resolves a configuration"() {
        def stdOut = new TestOutputStream()
        def stdErr = new TestOutputStream()
        singleProjectBuildInRootFolder("root") {
            buildFile << """
                apply plugin: 'java'
            """
        }

        when:
        withConnection {
            action(new FetchEclipseProject()).withArguments("--warning-mode", "all").setStandardOutput(stdOut).setStandardError(stdErr).run()
        }

        then:
        println stdOut.toString()
        !stdOut.toString().contains("was resolved without accessing the project in a safe manner")

        and:
        stdOut.toString().contains("CONFIGURE SUCCESSFUL")
    }
}
