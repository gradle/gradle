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
import org.gradle.tooling.model.eclipse.EclipseBuild
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseIntegrationTest extends ToolingApiSpecification {

    def canBuildEclipseSourceDirectoriesForAProject() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
'''

        when:
        EclipseBuild build = withConnection { connection -> connection.getModel(EclipseBuild.class) }
        EclipseProject rootProject = build.getRootProject()

        then:
        rootProject != null

        rootProject.sourceDirectories.size() == 4
        rootProject.sourceDirectories[0].path == 'src/main/java'
        rootProject.sourceDirectories[0].directory == projectDir.file('src/main/java')
        rootProject.sourceDirectories[1].path == 'src/main/resources'
        rootProject.sourceDirectories[1].directory == projectDir.file('src/main/resources')
        rootProject.sourceDirectories[2].path == 'src/test/java'
        rootProject.sourceDirectories[2].directory == projectDir.file('src/test/java')
        rootProject.sourceDirectories[3].path == 'src/test/resources'
        rootProject.sourceDirectories[3].directory == projectDir.file('src/test/resources')
    }

    def canBuildEclipseExternalDependenciesForAProject() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
repositories { mavenCentral() }
dependencies {
    compile 'commons-lang:commons-lang:2.5'
    runtime 'commons-io:commons-io:1.4'
}
'''

        when:
        EclipseBuild build = withConnection { connection -> connection.getModel(EclipseBuild.class) }
        EclipseProject rootProject = build.getRootProject()

        then:
        rootProject != null

        rootProject.classpath.size() == 2
        rootProject.classpath[0] instanceof ExternalDependency
        rootProject.classpath[0].file.name == 'commons-io-1.4.jar'
        rootProject.classpath[1] instanceof ExternalDependency
        rootProject.classpath[1].file.name == 'commons-lang-2.5.jar'
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
        EclipseBuild build = withConnection { connection -> connection.getModel(EclipseBuild.class) }
        EclipseProject rootProject = build.getRootProject()

        then:
        EclipseProject project = rootProject.childProjects[0]

        project.projectDependencies.size() == 2

        project.projectDependencies[0].path == 'root'
        project.projectDependencies[0].targetProject == rootProject

        project.projectDependencies[1].path == 'b'
        project.projectDependencies[1].targetProject == project.childProjects[0]
    }

    def canBuildEclipseProjectHierarchyForAMultiProjectBuild() {
        def projectDir = dist.testDir
        projectDir.file('settings.gradle').text = '''
            include "child1", "child2", "child1:child1"
            rootProject.name = 'root'
'''

        when:
        EclipseBuild build = withConnection { connection -> connection.getModel(EclipseBuild.class) }
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
