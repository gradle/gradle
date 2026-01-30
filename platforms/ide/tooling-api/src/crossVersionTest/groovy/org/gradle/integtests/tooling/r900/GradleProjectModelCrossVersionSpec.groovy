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

package org.gradle.integtests.tooling.r900

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion(">=9.0.0")
class GradleProjectModelCrossVersionSpec extends ToolingApiSpecification {
    def "can build the GradleProject model with configuration-time resolution in lazy task when parallel=#parallel"() {
        projectDir.file("gradle.properties") << """
            org.gradle.parallel=$parallel
            """
        includeProjects("a")
        settingsFile << '''
            rootProject.name = 'root'
            '''
        projectDir.file('a/build.gradle').text = """
            def something = configurations.create('something')
            tasks.register('aTask') {
               something.getFiles()
            }
            """

        when:
        GradleProject model = loadToolingModel(GradleProject)

        then:
        model.findByPath(":a") != null
        model.findByPath(":a").tasks.any { it.name == "aTask" }

        where:
        parallel << [true, false]
    }

    def "can build the GradleProject model with configuration-time resolution in top-level when parallel=#parallel"() {
        projectDir.file("gradle.properties") << """
            org.gradle.parallel=$parallel
            """
        includeProjects("a")
        settingsFile << '''
            rootProject.name = 'root'
            '''
        projectDir.file('a/build.gradle').text = """
            def something = configurations.create('something')
            something.getFiles()
            """

        when:
        GradleProject model = loadToolingModel(GradleProject)

        then:
        model.findByPath(":a") != null

        where:
        parallel << [true, false]
    }
}
