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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.CollectionUtils
import spock.lang.Ignore

@Ignore("We do not support forTasks(String) on a composite connection for now")
class ExecuteTaskModelBuilderCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    def "can call tasks before building composite model"() {
        given:
        def singleBuild = populate("single") {
            settingsFile << "rootProject.name = '${rootProjectName}'"
            buildFile << """
        apply plugin: 'java'

        description = "not set"

        task setDescription() << {
            project.description = "Set from task"
        }
"""
        }
        when:
        def modelResults = withCompositeConnection(singleBuild) { connection ->
            def modelBuilder = connection.models(EclipseProject)
            modelBuilder.forTasks("setDescription")
            modelBuilder.get()
        }
        then:
        CollectionUtils.single(modelResults).model.description == "Set from task"
    }
}
