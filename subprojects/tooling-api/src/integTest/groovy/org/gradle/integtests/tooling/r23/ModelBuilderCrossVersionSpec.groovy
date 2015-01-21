/*
 * Copyright 2014 the original author or authors.
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


package org.gradle.integtests.tooling.r23

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment

class ModelBuilderCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=2.3")
    def "empty list of tasks to execute when asking for BuildEnvironment is treated like null tasks and does not fail"() {
        projectDir.file('build.gradle')

        when:
        BuildEnvironment model = toolingApi.withConnection { ProjectConnection connection ->
            ModelBuilder<BuildEnvironment> modelBuilder = connection.model(BuildEnvironment.class)
            modelBuilder.forTasks(new String[0])
            modelBuilder.get()
        }

        then:
        model != null
    }

    @ToolingApiVersion(">=2.3")
    def "empty list of tasks to execute when asking for model from target Gradle is treated like null tasks and executes no tasks"() {
        def message = 'task alpha invoked'

        projectDir.file('build.gradle') << """
            defaultTasks 'alpha'
            task alpha() << { println '${message}'}
        """

        def outputStream = new ByteArrayOutputStream()

        when:
        GradleProject model = toolingApi.withConnection { ProjectConnection connection ->
            ModelBuilder<GradleProject> modelBuilder = connection.model(GradleProject.class)
            modelBuilder.forTasks(new String[0])
            modelBuilder.setStandardOutput(outputStream)
            modelBuilder.get()
        }

        then:
        model != null
        assert !outputStream.toString().contains(message)
    }

}
