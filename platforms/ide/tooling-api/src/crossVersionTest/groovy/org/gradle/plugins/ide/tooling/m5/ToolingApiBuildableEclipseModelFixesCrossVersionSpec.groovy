/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.plugins.ide.tooling.m5

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

class ToolingApiBuildableEclipseModelFixesCrossVersionSpec extends ToolingApiSpecification {
    @Issue("GRADLE-1529")
    //this is just one of the ways of fixing the problem. See the issue for details
    def "should not show not executable tasks"() {
        file('build.gradle') << '''
task a
task b
'''
        when:
        def project = withConnection { connection -> connection.getModel(GradleProject.class) }

        then:
        def tasks = project.tasks*.name
        tasks.contains('a')
        tasks.contains('b')
        !tasks.contains('cleanEclipse')
        !tasks.contains('eclipse')
    }

    @Issue("GRADLE-1529")
    //this is just one of the ways of fixing the problem. See the issue for details
    def "should hide not executable tasks when necessary for a multi module build"() {
        file('build.gradle').text = '''
project(':api') {
    apply plugin: 'java'
    apply plugin: 'eclipse'
}
'''
        createDirs("api", "impl")
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)

        then:
        def rootTasks = eclipseProject.gradleProject.tasks.collect { it.name }

        EclipseProject api = eclipseProject.children.find { it.name == "api" }
        def apiTasks = api.gradleProject.tasks.collect { it.name }

        EclipseProject impl = eclipseProject.children.find { it.name == "impl" }
        def implTasks = impl.gradleProject.tasks.collect { it.name }

        ['eclipse', 'cleanEclipse', 'eclipseProject', 'cleanEclipseProject'].each {
            assert !rootTasks.contains(it)
            assert !implTasks.contains(it)

            assert apiTasks.contains(it)
        }
    }
}
