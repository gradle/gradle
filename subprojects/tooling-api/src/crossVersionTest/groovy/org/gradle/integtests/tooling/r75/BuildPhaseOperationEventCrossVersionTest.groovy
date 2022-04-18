/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r75

import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.events.OperationType

@TargetGradleVersion(">=7.4")
class BuildPhaseOperationEventCrossVersionTest extends ToolingApiSpecification {
    def "generates build phase events"() {
        setupProject()
        setTargetDist(new UnderDevelopmentGradleDistribution())

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(":a:taskC")
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        events.events.size() == 6
    }

    def setupProject() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b', 'c'
        """
        file("a/build.gradle") << """
            tasks.register("taskA")
            tasks.register("taskB") {
                dependsOn 'taskA'
            }
            tasks.register("taskC") {
                dependsOn 'taskB'
            }
        """
        file("b/build.gradle") << """
            tasks.register("taskA")
            tasks.register("taskB") {
                dependsOn 'taskA'
            }
            tasks.register("taskC") {
                dependsOn 'taskB'
            }
        """
        file("c/build.gradle") << """
            tasks.register("taskA")
            tasks.register("taskB") {
                dependsOn 'taskA'
            }
            tasks.register("taskC") {
                dependsOn 'taskB'
            }
        """
    }
}
