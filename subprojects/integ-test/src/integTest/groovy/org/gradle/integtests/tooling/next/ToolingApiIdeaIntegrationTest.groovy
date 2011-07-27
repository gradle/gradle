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
package org.gradle.integtests.tooling.next

import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.idea.IdeaLibraryDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

class ToolingApiIdeaModelIntegrationTest extends ToolingApiSpecification {

    def setup() {
//        toolingApi.withConnector {
//            it.useInstallation(new File(dist.gradleHomeDir.absolutePath))
//            it.embedded(false)
//            it.daemonMaxIdleTime(300, TimeUnit.SECONDS)
//            DaemonGradleExecuter.registerDaemon(dist.userHomeDir)
//        }
    }

    def "builds the model even if idea plugin not applied"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }

        then:
        project.parent == null
        project.name == 'test project'
        project.id   == ':'
        project.description == null
        project.children.size() == 1
        project.children[0] instanceof IdeaModule
    }

    def "provides basic project information"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.project {
  languageLevel = '1.5'
  jdkName = '1.6'
}
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }

        then:
        project.languageLevel == 'JDK_1_5'
        project.jdkName == '1.6'
    }

    def "provides all modules"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
subprojects {
    apply plugin: 'java'
}
'''
        projectDir.file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }

        then:
        project.children.size() == 3
        project.children.any { it.name == 'api' }
        project.children.any { it.name == 'impl' }
    }

    def "provides basic module information"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.module.inheritOutputDirs = false
idea.module.outputDir = file('someDir')
idea.module.testOutputDir = file('someTestDir')
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children[0]

        then:
        module.contentRoots == [projectDir]
        module.parent instanceof IdeaProject
        module.parent == project
        module.children.empty
        module.description == null

        !module.inheritOutputDirs
        module.outputDir == projectDir.file('someDir')
        module.testOutputDir == projectDir.file('someTestDir')

        module.moduleFileDir == dist.testDir
    }

    def "provides source dir information"() {
        def projectDir = dist.testDir
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
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children[0]

        then:
        module.sourceDirectories.size() == 2
        module.sourceDirectories.any { it == projectDir.file('src/main/java') }
        module.sourceDirectories.any { it == projectDir.file('src/main/resources') }

        module.testDirectories.size() == 2
        module.testDirectories.any { it == projectDir.file('src/test/java') }
        module.testDirectories.any { it == projectDir.file('src/test/resources') }
    }

    def "provides exclude dir information"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.module.excludeDirs += file('foo')
"""

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children[0]

        then:
        module.excludeDirectories.any { it.path.endsWith 'foo' }
    }

    def "provides dependencies"() {
        def projectDir = dist.testDir
        def fakeRepo = projectDir.file("repo")

        new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 1.0).publishArtifact()
        new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 1.0, 'sources').publishArtifact()
        new MavenRepository(fakeRepo).module("foo.bar", "coolLib", 1.0, 'javadoc').publishArtifact()


        projectDir.file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    repositories {
        mavenRepo urls: "${fakeRepo.toURI()}"
    }

    dependencies {
        compile project(':api')
        testCompile 'foo.bar:coolLib:1.0'
    }

    idea.module.downloadJavadoc = true
}
"""
        projectDir.file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children.find { it.name == 'impl' }

        then:
        def libs = module.dependencies
        IdeaLibraryDependency lib = libs.find {it instanceof IdeaLibraryDependency}

        lib.file.exists()
        lib.file.path.endsWith('coolLib-1.0.jar')

        lib.source.exists()
        lib.source.path.endsWith('coolLib-1.0-sources.jar')

        lib.javadoc.exists()
        lib.javadoc.path.endsWith('coolLib-1.0-javadoc.jar')

        lib.scope.toString() == 'TEST'

        IdeaModuleDependency mod = libs.find {it instanceof IdeaModuleDependency}
        mod.dependencyModuleName == 'api'
    }
}