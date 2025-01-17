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

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion("<8.12")
@TargetGradleVersion('>=5.0')
class ModelProgressEventsCrossVersionSpec extends ToolingApiSpecification {
    def "generates model progress events"() {
        given:
        def listener = ProgressEvents.create()
        settingsFile << """rootProject.name = "root" """
        when:
        def gradleProject = withConnection {
            ProjectConnection connection ->
                connection.model(GradleProject).
                    addProgressListener(listener, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).get()
        }
        then:
        gradleProject
        def buildModelOperation = listener.operation("Build model 'org.gradle.tooling.model.GradleProject' for root project 'root'")
        def tasks = buildModelOperation.descendants {
            it.descriptor.displayName.startsWith("Realize task")
        }
        !tasks.empty
    }
}
