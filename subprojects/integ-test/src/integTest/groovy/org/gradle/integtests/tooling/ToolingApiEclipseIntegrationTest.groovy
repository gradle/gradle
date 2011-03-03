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
package org.gradle.integtests.tooling

import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.GradleConnector

class ToolingApiEclipseIntegrationTest extends ToolingApiSpecification {

    def canBuildProjectMetaDataForAProject() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject.name == 'test project'
        eclipseProject.projectDirectory == projectDir
    }

    def canBuildEclipseSourceDirectoriesForAProject() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
'''

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject != null

        eclipseProject.sourceDirectories.size() == 4
        eclipseProject.sourceDirectories[0].path == 'src/main/java'
        eclipseProject.sourceDirectories[0].directory == projectDir.file('src/main/java')
        eclipseProject.sourceDirectories[1].path == 'src/main/resources'
        eclipseProject.sourceDirectories[1].directory == projectDir.file('src/main/resources')
        eclipseProject.sourceDirectories[2].path == 'src/test/java'
        eclipseProject.sourceDirectories[2].directory == projectDir.file('src/test/java')
        eclipseProject.sourceDirectories[3].path == 'src/test/resources'
        eclipseProject.sourceDirectories[3].directory == projectDir.file('src/test/resources')
    }

    def canBuildEclipseExternalDependenciesForAProject() {
        def projectDir = dist.testDir
        projectDir.file('settings.gradle').text = '''
include "a"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = '''
allprojects { apply plugin: 'java' }
repositories { mavenCentral() }
dependencies {
    compile 'commons-lang:commons-lang:2.5'
    compile project(':a')
    runtime 'commons-io:commons-io:1.4'
}
'''

        when:
        EclipseProject eclipseProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        eclipseProject != null

        eclipseProject.classpath.size() == 2
        eclipseProject.classpath[0] instanceof ExternalDependency
        eclipseProject.classpath[0].file.name == 'commons-io-1.4.jar'
        eclipseProject.classpath[1] instanceof ExternalDependency
        eclipseProject.classpath[1].file.name == 'commons-lang-2.5.jar'
    }

    def canBuildEclipseProjectDependenciesForAProject() {
        def projectDir = dist.testDir
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
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        EclipseProject project = rootProject.children[0]

        project.projectDependencies.size() == 2

        project.projectDependencies[0].path == 'root'
        project.projectDependencies[0].targetProject == rootProject

        project.projectDependencies[1].path == 'b'
        project.projectDependencies[1].targetProject == project.children[0]
    }

    def canBuildEclipseProjectHierarchyForAMultiProjectBuild() {
        def projectDir = dist.testDir
        projectDir.file('settings.gradle').text = '''
            include "child1", "child2", "child1:child1"
            rootProject.name = 'root'
'''
        projectDir.file('child1').mkdirs()

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        rootProject != null
        rootProject.name == 'root'
        rootProject.parent == null

        rootProject.children.size() == 2

        EclipseProject child1 = rootProject.children[0]
        child1.name == 'child1'
        child1.parent == rootProject
        child1.children.size() == 1

        EclipseProject child1Child1 = child1.children[0]
        child1Child1.name == 'child1'
        child1Child1.parent == child1
        child1Child1.children.size() == 0

        EclipseProject child2 = rootProject.children[1]
        child2.name == 'child2'
        child2.parent == rootProject
        child2.children.size() == 0

        when:
        GradleConnector connector = GradleConnector.newConnector()
        connector.forProjectDirectory(projectDir.file('child1'))
        EclipseProject child = withConnectionRaw(connector) { connection -> connection.getModel(EclipseProject.class) }

        then:
        child.name == 'child1'
        child.parent != null
        child.parent.name == 'root'
        child.children.size() == 1
    }
}
