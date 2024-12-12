/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.tooling.r812

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion('>=8.12')
class BuildActionModelFetchProgressEventsCrossVersionSpec extends ToolingApiSpecification {

    def setup() {
        settingsFile << "rootProject.name = 'root'"
    }

    def "build model requests have build operations"() {
        given:
        def listener = ProgressEvents.create()

        when:
        def model = loadToolingModel(GradleProject) {
            it.addProgressListener(listener)
        }

        then:
        model != null

        and:
        listener.operation("Fetch model 'org.gradle.tooling.model.GradleProject' for default scope")
            .descendant("Configure build")
    }

    def "build action model requests have build operations"() {
        given:
        def listener = ProgressEvents.create()

        when:
        def models = succeeds { connection ->
            connection.action(new FetchBuildAndProjectModels())
                .addProgressListener(listener)
                .run()
        }

        then:
        models != null

        and:
        listener.operation("Fetch model 'org.gradle.tooling.model.gradle.GradleBuild' for default scope")
            .descendant("Load build")
        listener.operation("Fetch model 'org.gradle.tooling.model.gradle.GradleBuild' for build scope")
        listener.operation("Fetch model 'org.gradle.tooling.model.GradleProject' for project scope")
            .descendant("Configure build")
    }

    def "phased build action model requests have build operations"() {
        given:
        def listener = ProgressEvents.create()

        when:
        def models = []
        succeeds { connection ->
            connection.action()
                .projectsLoaded(new FetchGradleBuild()) {
                    models.add(it)
                }
                .buildFinished(new FetchBuildEnvironment()) {
                    models.add(it)
                }
                .build()
                .addProgressListener(listener)
                .run()
            true
        }

        then:
        models.size() == 2

        and:
        listener.operation("Fetch model 'org.gradle.tooling.model.gradle.GradleBuild' for default scope")
            .descendant("Load build")

        listener.operation("Fetch model 'org.gradle.tooling.model.build.BuildEnvironment' for default scope")
            .descendant("Configure build")
    }
}
