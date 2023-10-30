/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68


import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@ToolingApiVersion(">=3.3")
@TargetGradleVersion('>=6.8')
class CompositeBuildModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    def "can run task from included build when querying a model"() {
        given:
        settingsFile << "includeBuild('other-build')"
        file('other-build/settings.gradle') << """
            rootProject.name = 'other-build'
            include 'sub'
        """
        file('other-build/sub/build.gradle') << """
            tasks.register('doSomething') {
                doLast {
                    println 'do something'
                }
            }
        """

        when:
        toolingApi.withConnection { ProjectConnection connection ->
            ModelBuilder<GradleProject> modelBuilder = connection.model(GradleProject.class)
            collectOutputs(modelBuilder)
            modelBuilder.forTasks([':other-build:sub:doSomething'])
            modelBuilder.get()
        }

        then:
        stdout.toString().contains("do something")
    }

}
