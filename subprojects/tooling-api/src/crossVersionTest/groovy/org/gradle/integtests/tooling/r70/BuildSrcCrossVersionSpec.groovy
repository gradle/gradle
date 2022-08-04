/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.tooling.r70

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

@TargetGradleVersion('>=7.0')
class BuildSrcCrossVersionSpec extends ToolingApiSpecification {
    def buildSrc = file("buildSrc")

    def setup() {
        settingsFile << """
            throw new GradleException("do not evaluate")
        """
        buildSrc.file("build.gradle") << '''
            plugins {
                id 'java'
            }
        '''
    }

    def "buildSrc without settings file can execute standalone"() {
        when:
        withConnectionToBuildSrc { connection ->
            def build = connection.newBuild()
            build.forTasks("help").run()
        }
        then:
        noExceptionThrown()
    }

    def "buildSrc with settings file can execute standalone"() {
        buildSrc.file("settings.gradle").touch()
        when:
        withConnectionToBuildSrc { connection ->
            def build = connection.newBuild()
            build.forTasks("help").run()
        }
        then:
        noExceptionThrown()
    }

    def "can request model from buildSrc without settings file"() {
        expect:
        def gradleProject = withConnectionToBuildSrc { connection ->
             def modelBuilder = connection.model(GradleProject)
            modelBuilder.get()
        }
        gradleProject.projectDirectory == buildSrc
        gradleProject.path == ':'
        gradleProject.children.size() == 0
    }

    def "can request model from buildSrc with settings file"() {
        buildSrc.file("settings.gradle").touch()
        expect:
        def gradleProject = withConnectionToBuildSrc { connection ->
            connection.getModel(GradleProject)
        }
        gradleProject.projectDirectory == buildSrc
        gradleProject.path == ':'
        gradleProject.children.size() == 0
    }

    private <T> T withConnectionToBuildSrc(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> c) {
        def connector = toolingApi.connector(buildSrc)
        return withConnection(connector, c)
    }
}
