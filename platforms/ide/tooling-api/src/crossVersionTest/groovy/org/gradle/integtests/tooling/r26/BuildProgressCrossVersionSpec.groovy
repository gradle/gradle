/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r26

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection

class BuildProgressCrossVersionSpec extends ToolingApiSpecification {
    def "generates init script operation when there are init scripts"() {
        file("init.gradle") << "println 'init'"

        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        !events.operations.find { it.descriptor.displayName == "Run init scripts" }

        when:
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .withArguments("--init-script", file("init.gradle").toString())
                        .addProgressListener(events)
                        .run()
        }

        then:
        events.assertIsABuild()

        def root = events.operation("Run build")
        root.descendant("Run init scripts")
    }

    def "generates buildSrc operation when there is a nested buildSrc build"() {
        when:
        def events = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        !events.operations.find { it.descriptor.displayName == "Build buildSrc" }

        when:
        events.clear()
        file("buildSrc/build.gradle") << "println 'buildSrc'"
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                        .addProgressListener(events)
                        .run()
        }

        then:
        events.assertIsABuild()

        def root = events.operation("Run build")
        root.descendant("Build buildSrc")
    }
}
