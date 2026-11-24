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

package org.gradle.integtests.tooling.r910

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import spock.lang.Issue

@TargetGradleVersion(">=9.10.0")
class IncludedBuildLoadingProgressCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/39634")
    def "names the included build in the operations of its project loading and init scripts"() {
        given:
        file("init.gradle") << "println 'init'"
        settingsFile << """
            includeBuild "child"
        """
        buildFile << """
            tasks.register("hello") {
                dependsOn gradle.includedBuild("child").task(":hello")
            }
        """
        file("child/build.gradle") << """
            tasks.register("hello")
        """

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .forTasks("hello")
                    .withArguments("--init-script", file("init.gradle").toString())
                    .addProgressListener(events)
                    .run()
        }

        then:
        events.assertIsABuild()
        def displayNames = events.operations*.descriptor*.displayName
        displayNames.containsAll(["Load projects", "Run init scripts", "Load projects (:child)", "Run init scripts (:child)"])
    }
}
