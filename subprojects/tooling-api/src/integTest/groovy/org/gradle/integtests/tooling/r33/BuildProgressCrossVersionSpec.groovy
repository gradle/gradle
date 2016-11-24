/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection

@TargetGradleVersion(">=3.3")
class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
    def "generates event for project configuration for single project build"() {
        given:
        settingsFile << "rootProject.name = 'single'"

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        def configureRootProject = events.operation("Configure root project 'single'")
        configureRootProject.descriptor.parent == events.operation("Configure build").descriptor
    }

    def "generates events for project configuration for multi-project build"() {
        given:
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        events.operation("Configure root project 'multi'")
        events.operation("Configure project ':a'")
        events.operation("Configure project ':b'")
    }
}
