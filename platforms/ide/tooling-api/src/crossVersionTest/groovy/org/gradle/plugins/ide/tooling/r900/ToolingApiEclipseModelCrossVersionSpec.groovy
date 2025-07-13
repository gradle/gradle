/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.plugins.ide.tooling.r900

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r56.DefaultEclipseWorkspace
import org.gradle.integtests.tooling.r56.DefaultEclipseWorkspaceProject
import org.gradle.integtests.tooling.r56.IntermediateResultHandlerCollector
import org.gradle.integtests.tooling.r56.ParameterizedLoadCompositeEclipseModels
import org.gradle.tooling.model.eclipse.RunClosedProjectBuildDependencies

@TargetGradleVersion('>=9.0.0')
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def "can build RunClosedProjectBuildDependencies when --parallel is #parallel"() {
        includeProjects("other")
        [buildFile, file("other/build.gradle")].each {
            it << """
                plugins {
                    id("java-library")
                }
            """
        }

        def workspace = new DefaultEclipseWorkspace(
            temporaryFolder.file("workspace"),
            [new DefaultEclipseWorkspaceProject("root", projectDir, true)]
        )

        expect:
        succeeds { connection ->
            def collector = new IntermediateResultHandlerCollector<Collection<RunClosedProjectBuildDependencies>>()

            def builder = connection.action()
                .projectsLoaded(new ParameterizedLoadCompositeEclipseModels(workspace, RunClosedProjectBuildDependencies), collector)
                .build()

            if (parallel) {
                builder.withArguments("--parallel")
            }

            builder.run()
            collector.result
        }

        where:
        parallel << [true, false]
    }

}
