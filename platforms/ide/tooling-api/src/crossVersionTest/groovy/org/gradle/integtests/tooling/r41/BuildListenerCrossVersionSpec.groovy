/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r41

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestOutputStream
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r18.FetchBuildEnvironment
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion(">=4.1")
class BuildListenerCrossVersionSpec extends ToolingApiSpecification {
    TestOutputStream output

    def setup() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'sub'
        """
        buildFile << """
            gradle.buildFinished() {
                println "Hello from root"
            }
        """
        file("sub/build.gradle") << """
            gradle.buildFinished() {
                println "Hello from sub"
            }
        """
        file("gradle.properties") << """
            org.gradle.configureondemand = true
        """
        output = new TestOutputStream()
    }

    def "build listeners are called when using configure-on-demand model builders"() {
        when:
        withConnection {
            ProjectConnection connection ->
                connection.model(GradleProject)
                    .setStandardOutput(output)
                    .get()
        }

        then:
        listenersWereCalled()
    }

    def "build listeners are called when using configure-on-demand with build actions"() {
        when:
        withConnection {
            ProjectConnection connection ->
                connection.action(new FetchBuildEnvironment())
                    .setStandardOutput(output)
                    .run()
        }

        then:
        listenersWereCalled()
    }

    def "build listeners are called when using configure-on-demand and running tasks"() {
        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .setStandardOutput(output)
                    .forTasks("help")
                    .run()
        }

        then:
        listenersWereCalled()
    }

    void listenersWereCalled() {
        def result = output.toString()
        assert result.contains("Hello from root")
        assert result.contains("Hello from sub")
    }
}
