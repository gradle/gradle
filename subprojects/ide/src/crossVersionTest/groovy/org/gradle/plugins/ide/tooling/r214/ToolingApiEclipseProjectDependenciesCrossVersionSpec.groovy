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
package org.gradle.plugins.ide.tooling.r214

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

@ToolingApiVersion("<=3.0")
@TargetGradleVersion(">=2.6 <=3.0")
class ToolingApiEclipseProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "can build the eclipse project dependencies for a java project"() {
        projectDir.file('settings.gradle').text = '''
include "a", "a:b"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
}
project(':a') {
    dependencies {
        compile project(':')
        compile project(':a:b')
    }
}
'''

        when:
        HierarchicalEclipseProject eclipseProjectRoot = loadToolingModel(HierarchicalEclipseProject)
        HierarchicalEclipseProject eclipseProjectA = eclipseProjectRoot.children[0]
        HierarchicalEclipseProject eclipseProjectB = eclipseProjectA.children[0]

        then:
        eclipseProjectRoot.gradleProject.path == ':'
        eclipseProjectA.gradleProject.path == ':a'
        eclipseProjectB.gradleProject.path == ':a:b'

        eclipseProjectA.projectDependencies.size() == 2
        eclipseProjectA.projectDependencies.find { it.path == 'root' }
        eclipseProjectA.projectDependencies.find { it.path == 'b' }
    }
}
