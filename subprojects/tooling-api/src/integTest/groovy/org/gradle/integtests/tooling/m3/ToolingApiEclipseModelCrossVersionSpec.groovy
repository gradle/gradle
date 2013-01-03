/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.tooling.m3

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    def "can build the eclipse model for a java project"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        HierarchicalEclipseProject minimalProject = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        minimalProject.name == 'test project'
        minimalProject.description == 'this is a project'
        minimalProject.projectDirectory == projectDir
        minimalProject.parent == null
        minimalProject.children.empty

        when:
        EclipseProject fullProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        fullProject.name == 'test project'
        fullProject.description == 'this is a project'
        fullProject.projectDirectory == projectDir
        fullProject.parent == null
        fullProject.children.empty
    }

    def "can build the eclipse model for an empty project"() {
        when:
        HierarchicalEclipseProject minimalProject = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        minimalProject != null

        minimalProject.description == null
        minimalProject.parent == null
        minimalProject.children.empty
        minimalProject.sourceDirectories.empty
        minimalProject.projectDependencies.empty

        when:
        EclipseProject fullProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        fullProject != null

        fullProject.description == null
        fullProject.parent == null
        fullProject.children.empty
        fullProject.sourceDirectories.empty
        fullProject.classpath.empty
        fullProject.projectDependencies.empty
    }

    def "does not run any tasks when fetching model"() {
        when:
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
gradle.taskGraph.beforeTask { throw new RuntimeException() }
'''
        HierarchicalEclipseProject project = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        project != null
    }

    def "can build the eclipse source directories for a java project"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = "apply plugin: 'java'"

        projectDir.create {
            src {
                main {
                    java {}
                    resources {}
                }
                test {
                    java {}
                    resources {}
                }
            }
        }

        when:
        HierarchicalEclipseProject minimalProject = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        minimalProject != null

        minimalProject.sourceDirectories.size() == 4
        minimalProject.sourceDirectories[0].path == 'src/main/java'
        minimalProject.sourceDirectories[0].directory == projectDir.file('src/main/java')
        minimalProject.sourceDirectories[1].path == 'src/main/resources'
        minimalProject.sourceDirectories[1].directory == projectDir.file('src/main/resources')
        minimalProject.sourceDirectories[2].path == 'src/test/java'
        minimalProject.sourceDirectories[2].directory == projectDir.file('src/test/java')
        minimalProject.sourceDirectories[3].path == 'src/test/resources'
        minimalProject.sourceDirectories[3].directory == projectDir.file('src/test/resources')

        when:
        EclipseProject fullProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        fullProject != null

        fullProject.sourceDirectories.size() == 4
        fullProject.sourceDirectories[0].path == 'src/main/java'
        fullProject.sourceDirectories[0].directory == projectDir.file('src/main/java')
        fullProject.sourceDirectories[1].path == 'src/main/resources'
        fullProject.sourceDirectories[1].directory == projectDir.file('src/main/resources')
        fullProject.sourceDirectories[2].path == 'src/test/java'
        fullProject.sourceDirectories[2].directory == projectDir.file('src/test/java')
        fullProject.sourceDirectories[3].path == 'src/test/resources'
        fullProject.sourceDirectories[3].directory == projectDir.file('src/test/resources')
    }

    def "can build the eclipse external dependencies for a java project"() {
        def projectDir = dist.testWorkDir
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
        eclipseProject.classpath.every { it instanceof ExternalDependency }
        eclipseProject.classpath.collect { it.file.name } as Set == ['commons-lang-2.5.jar', 'commons-io-1.4.jar' ] as Set
        eclipseProject.classpath.collect { it.source?.name } as Set == ['commons-lang-2.5-sources.jar', 'commons-io-1.4-sources.jar'] as Set
        eclipseProject.classpath.collect { it.javadoc?.name } as Set == [null, null] as Set
    }

    //TODO SF: write a test that checks if minimal project has necessary project dependencies

    def "can build the minimal Eclipse model for a java project with the idea plugin applied"() {
        def projectDir = dist.testWorkDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'idea'

dependencies {
    compile files { throw new RuntimeException('should not be resolving this') }
}
'''

        when:
        HierarchicalEclipseProject minimalProject = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        minimalProject != null
    }

    def "can build the eclipse project dependencies for a java project"() {
        def projectDir = dist.testWorkDir
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
        HierarchicalEclipseProject minimalModel = withConnection { connection -> connection.getModel(HierarchicalEclipseProject.class) }

        then:
        HierarchicalEclipseProject minimalProject = minimalModel.children[0]

        minimalProject.projectDependencies.size() == 2

        minimalProject.projectDependencies.any { it.path == 'root' && it.targetProject == minimalModel }
        minimalProject.projectDependencies.any { it.path == 'b' && it.targetProject == minimalProject.children[0] }

        when:
        EclipseProject fullModel = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        EclipseProject fullProject = fullModel.children[0]

        fullProject.projectDependencies.size() == 2

        fullProject.projectDependencies.any { it.path == 'root' && it.targetProject == fullModel }
        fullProject.projectDependencies.any { it.path == 'b' && it.targetProject == fullProject.children[0] }
    }

    def "can build project dependencies with targetProject references for complex scenarios"() {
        def projectDir = dist.testWorkDir
        projectDir.file('settings.gradle').text = '''
include "c", "a", "a:b"
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
        compile project(':c')
    }
}
project(':c') {
    dependencies {
        compile project(':a:b')
    }
}
'''

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        def projectC = rootProject.children.find { it.name == 'c'}
        def projectA = rootProject.children.find { it.name == 'a'}
        def projectAB = projectA.children.find { it.name == 'b' }

        projectC.projectDependencies.any {it.targetProject == projectAB}

        projectA.projectDependencies.any {it.targetProject == projectAB}
        projectA.projectDependencies.any {it.targetProject == projectC}
        projectA.projectDependencies.any {it.targetProject == rootProject}
    }

    def "can build the eclipse project hierarchy for a multi-project build"() {
        def projectDir = dist.testWorkDir
        projectDir.file('settings.gradle').text = '''
            include "child1", "child2", "child1:grandChild1"
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
        child1Child1.name == 'grandChild1'
        child1Child1.parent == child1
        child1Child1.children.size() == 0

        EclipseProject child2 = rootProject.children[1]
        child2.name == 'child2'
        child2.parent == rootProject
        child2.children.size() == 0

        when:
        toolingApi.withConnector { connector ->
            connector.searchUpwards(true)
            connector.forProjectDirectory(projectDir.file('child1'))
        }
        EclipseProject child = toolingApi.withConnection { connection -> connection.getModel(EclipseProject.class) }

        then:
        child.name == 'child1'
        child.parent != null
        child.parent.name == 'root'
        child.children.size() == 1
    }
}
