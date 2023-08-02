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
package org.gradle.plugins.ide.tooling.m3

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.ExternalDependency
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def shouldCheckForDeprecationWarnings(){
        false
    }

    def "can build the eclipse model for a java project"() {

        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        HierarchicalEclipseProject minimalProject = loadToolingModel(HierarchicalEclipseProject)

        then:
        minimalProject.name == 'test project'
        minimalProject.description == 'this is a project'
        minimalProject.projectDirectory == projectDir
        minimalProject.parent == null
        minimalProject.children.empty

        when:
        EclipseProject fullProject = loadToolingModel(EclipseProject)

        then:
        fullProject.name == 'test project'
        fullProject.description == 'this is a project'
        fullProject.projectDirectory == projectDir
        fullProject.parent == null
        fullProject.children.empty
    }

    def "can build the eclipse model for an empty project"() {
        when:
        HierarchicalEclipseProject minimalProject = loadToolingModel(HierarchicalEclipseProject)

        then:
        minimalProject != null

        minimalProject.description == null
        minimalProject.parent == null
        minimalProject.children.empty
        minimalProject.sourceDirectories.empty
        minimalProject.projectDependencies.empty

        when:
        EclipseProject fullProject = loadToolingModel(EclipseProject)

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
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
gradle.taskGraph.beforeTask { throw new RuntimeException() }
'''
        HierarchicalEclipseProject project = loadToolingModel(HierarchicalEclipseProject)

        then:
        project != null
    }

    def "can build the eclipse source directories for a java project"() {

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
        HierarchicalEclipseProject minimalProject = loadToolingModel(HierarchicalEclipseProject)

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
        EclipseProject fullProject = loadToolingModel(EclipseProject)

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

        projectDir.file('settings.gradle').text = '''
include "a"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = """
allprojects { apply plugin: 'java' }
${mavenCentralRepository()}
dependencies {
    ${implementationConfiguration} 'commons-lang:commons-lang:2.5'
    ${implementationConfiguration} project(':a')
    ${runtimeConfiguration} 'commons-io:commons-io:1.4'
}
"""

        when:
        EclipseProject eclipseProject = loadToolingModel(EclipseProject)

        then:
        eclipseProject != null

        eclipseProject.classpath.size() == 2
        eclipseProject.classpath.every { it instanceof ExternalDependency }
        eclipseProject.classpath.collect { it.file.name } as Set == ['commons-lang-2.5.jar', 'commons-io-1.4.jar'] as Set
        eclipseProject.classpath.collect { it.source?.name } as Set == ['commons-lang-2.5-sources.jar', 'commons-io-1.4-sources.jar'] as Set
        eclipseProject.classpath.collect { it.javadoc?.name } as Set == [null, null] as Set
    }

    def "can build the minimal Eclipse model for a java project with the idea plugin applied"() {

        projectDir.file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

dependencies {
    ${implementationConfiguration} files { throw new RuntimeException('should not be resolving this') }
}
"""

        when:
        HierarchicalEclipseProject minimalProject = loadToolingModel(HierarchicalEclipseProject)

        then:
        minimalProject != null
    }

    def "can build the eclipse project dependencies for a java project"() {
        projectDir.file("gradle.properties") << """
            org.gradle.parallel=$parallel
        """
        projectDir.file('settings.gradle').text = '''
include "a", "a:b"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = """
allprojects {
    apply plugin: 'java'
}
project(':a') {
    dependencies {
        ${implementationConfiguration} project(':')
        ${implementationConfiguration} project(':a:b')
    }
}
"""

        when:
        HierarchicalEclipseProject minimalModel = loadToolingModel(HierarchicalEclipseProject)

        then:
        HierarchicalEclipseProject minimalProject = minimalModel.children[0]

        minimalProject.projectDependencies.size() == 2

        minimalProject.projectDependencies.any { it.path == 'root' }
        minimalProject.projectDependencies.any { it.path == 'b' }

        when:
        EclipseProject fullModel = loadToolingModel(EclipseProject)

        then:
        EclipseProject fullProject = fullModel.children[0]

        fullProject.projectDependencies.size() == 2

        fullProject.projectDependencies.any { it.path == 'root' }
        fullProject.projectDependencies.any { it.path == 'b' }

        where:
        parallel << [true, false]
    }

    def "can build project dependencies with targetProject references for complex scenarios"() {

        projectDir.file('settings.gradle').text = '''
include "c", "a", "a:b"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = """
allprojects {
    apply plugin: 'java'
}
project(':a') {
    dependencies {
        ${implementationConfiguration} project(':')
        ${implementationConfiguration} project(':a:b')
        ${implementationConfiguration} project(':c')
    }
}
project(':c') {
    dependencies {
        ${implementationConfiguration} project(':a:b')
    }
}
"""

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        def projectC = rootProject.children.find { it.name == 'c' }
        def projectA = rootProject.children.find { it.name == 'a' }

        projectC.projectDependencies.any { it.path == 'b' }

        projectA.projectDependencies.any { it.path == 'b' }
        projectA.projectDependencies.any { it.path == 'c' }
        projectA.projectDependencies.any { it.path == 'root' }
    }

    def "can build the eclipse project hierarchy for a multi-project build"() {

        projectDir.file('settings.gradle').text = '''
            include "child1", "child2", "child1:grandChild1"
            rootProject.name = 'root'
'''
        projectDir.file('child1').mkdirs()

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject != null
        rootProject.name == 'root'
        rootProject.parent == null

        rootProject.children.size() == 2
        def children = rootProject.children.sort { it.name }

        EclipseProject child1 = children[0]
        child1.name == 'child1'
        child1.parent == rootProject
        child1.children.size() == 1

        EclipseProject child1Child1 = child1.children[0]
        child1Child1.name == 'grandChild1'
        child1Child1.parent == child1
        child1Child1.children.size() == 0

        EclipseProject child2 = children[1]
        child2.name == 'child2'
        child2.parent == rootProject
        child2.children.size() == 0
    }

    def "can build the eclipse project hierarchy for a multi-project build and access child projects directly"() {

        projectDir.file('settings.gradle').text = '''
            include "child1", "child2", "child1:grandChild1"
            rootProject.name = 'root'
'''
        projectDir.file('child1').mkdirs()

        when:
        toolingApi.withConnector { connector ->
            connector.searchUpwards(true)
            connector.forProjectDirectory(projectDir.file('child1'))
        }
        EclipseProject child = loadToolingModel(EclipseProject)

        then:
        child.name == 'child1'
        child.parent != null
        child.parent.name == 'root'
        child.children.size() == 1
    }

    def "respects customized eclipse project name"() {
        settingsFile.text = "include ':foo', ':bar'"
        buildFile.text = """
allprojects {
    apply plugin:'java'
    apply plugin:'eclipse'
}

configure(project(':bar')) {
    eclipse {
        project {
            name = "customized-bar"
        }
    }
}
"""
        when:
        HierarchicalEclipseProject rootProject = loadToolingModel(HierarchicalEclipseProject)
        then:
        rootProject.children.any { it.name == 'foo' }
        rootProject.children.any { it.name == 'customized-bar' }
    }
}
