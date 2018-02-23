/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {
    def "eclipse project has access to gradle project and its tasks"() {

        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

task rootTask {}

project(':impl') {
    task implTask {}
}
"""
        file('settings.gradle').text = "include 'api', 'impl'; rootProject.name = 'root'"

        when:
        def root = loadToolingModel(EclipseProject)

        then:
        def impl = root.children.find { it.name == 'impl'}

        root.gradleProject.tasks.find { it.name == 'rootTask' && it.path == ':rootTask' && it.project == root.gradleProject }
        !root.gradleProject.tasks.find { it.name == 'implTask' }

        impl.gradleProject.tasks.find { it.name == 'implTask' && it.path == ':impl:implTask' && it.project == impl.gradleProject}
        !impl.gradleProject.tasks.find { it.name == 'rootTask' }
    }
}
