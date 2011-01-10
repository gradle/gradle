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
package org.gradle.integtests

import org.gradle.tooling.model.eclipse.EclipseBuild
import spock.lang.Specification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.junit.Rule
import org.gradle.tooling.model.ExternalDependency
import org.gradle.util.TemporaryFolder
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.GradleConnection

class ToolingApiEclipseIntegrationTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()

    def canBuildEclipseClasspathModelForABuild() {
        File projectDir = tmpDir.getDir()
        new File(projectDir, 'build.gradle').text = '''
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
                compile 'commons-lang:commons-lang:2.5'
                runtime 'commons-io:commons-io:1.4'
            }
'''

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.forProjectDirectory(projectDir).connect()
        EclipseBuild build = connection.getModel(EclipseBuild.class)
        EclipseProject rootProject = build.getRootProject()

        then:
        rootProject != null
        rootProject.classpath.size() == 2
        rootProject.classpath[0] instanceof ExternalDependency
        rootProject.classpath[0].file.name == 'commons-io-1.4.jar'
        rootProject.classpath[1] instanceof ExternalDependency
        rootProject.classpath[1].file.name == 'commons-lang-2.5.jar'
    }

    def canBuildEclipseProjectHierarchyForAMultiProjectBuild() {
        File projectDir = tmpDir.getDir()
        new File(projectDir, 'settings.gradle').text = '''
            include "child1", "child2", "child1:child1"
            rootProject.name = 'root'
'''

        when:
        GradleConnector connector = GradleConnector.newConnector()
        GradleConnection connection = connector.forProjectDirectory(projectDir).connect()
        EclipseBuild build = connection.getModel(EclipseBuild.class)
        EclipseProject rootProject = build.getRootProject()

        then:
        rootProject != null
        rootProject.name == 'root'
        rootProject.childProjects.size() == 2
        rootProject.childProjects[0].name == 'child1'
        rootProject.childProjects[0].childProjects.size() == 1

        rootProject.childProjects[0].childProjects[0].name == 'child1'
        rootProject.childProjects[0].childProjects[0].childProjects.size() == 0

        rootProject.childProjects[1].name == 'child2'
        rootProject.childProjects[1].childProjects.size() == 0
    }
}
