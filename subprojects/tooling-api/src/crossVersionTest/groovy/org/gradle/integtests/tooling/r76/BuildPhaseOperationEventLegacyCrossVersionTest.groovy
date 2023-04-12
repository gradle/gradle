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

package org.gradle.integtests.tooling.r76

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.events.OperationType

@ToolingApiVersion('>=7.6')
@TargetGradleVersion('>=3.0 <7.6')
class BuildPhaseOperationEventLegacyCrossVersionTest extends ToolingApiSpecification {

    def "doesn't generate build phase events for gradle versions that don't support them"() {
        setupProject()

        when:
        def events = ProgressEvents.create()
        withConnection {
            it.newBuild().forTasks(":a:taskC")
                .addProgressListener(events, OperationType.BUILD_PHASE)
                .run()
        }

        then:
        events.getAll().isEmpty()
    }

    def setupProject() {
        settingsFile << """
            rootProject.name = 'root'
            include 'a'
        """
        file("a/build.gradle") << """
            task taskA {}
            task taskB {
                dependsOn 'taskA'
            }
            task taskC {
                dependsOn 'taskB'
            }
        """
    }
}
