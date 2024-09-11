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
package org.gradle.plugins.ide.tooling.m5

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency
import org.gradle.util.GradleVersion

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def shouldCheckForDeprecationWarnings(){
        false
    }

    def "builds the model even if idea plugin not applied"() {

        buildFile.text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        settingsFile.text = 'rootProject.name = \"test project\"'

        when:
        IdeaProject project = loadToolingModel(IdeaProject)

        then:
        project.parent == null
        project.name == 'test project'
        project.description == null
        project.children.size() == 1
        project.children[0] instanceof IdeaModule
        project.children == project.modules
    }

    def "provides basic project information"() {

        buildFile.text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.project {
  languageLevel = '1.5'
  jdkName = '1.6'
}
"""

        when:
        IdeaProject project = loadToolingModel(IdeaProject)

        then:
        project.languageLevel.level == "JDK_1_5"
        project.jdkName == '1.6'
    }

    def "provides all modules"() {

        file('build.gradle').text = '''
subprojects {
    apply plugin: 'java'
}
'''
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = loadToolingModel(IdeaProject)

        then:
        project.children.size() == 3
        project.children.any { it.name == 'api' }
        project.children.any { it.name == 'impl' }
    }

    def "provides basic module information"() {

        file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.module.inheritOutputDirs = false
idea.module.outputDir = file('someDir')
idea.module.testOutputDir = file('someTestDir')
"""

        when:
        IdeaProject project = loadToolingModel(IdeaProject)
        def module = project.children[0]

        then:
        module.contentRoots.size() == 1
        module.contentRoots[0].rootDirectory == projectDir
        module.parent instanceof IdeaProject
        module.parent == project
        module.parent == module.project
        module.children.empty
        module.description == null

        !module.compilerOutput.inheritOutputDirs
        module.compilerOutput.outputDir == file('someDir')
        module.compilerOutput.testOutputDir == file('someTestDir')
    }

    @TargetGradleVersion(">=3.0 <5.0")
    def "provides source dir information"() {

        file('build.gradle').text = "apply plugin: 'java'"

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
        IdeaProject project = loadToolingModel(IdeaProject)
        IdeaModule module = project.children[0]
        IdeaContentRoot root = module.contentRoots[0]

        then:
        root.sourceDirectories.size() == 2
        root.sourceDirectories.any { it.directory == file('src/main/java') }
        root.sourceDirectories.any { it.directory == file('src/main/resources') }

        root.testDirectories.size() == 2
        root.testDirectories.any { it.directory == file('src/test/java') }
        root.testDirectories.any { it.directory == file('src/test/resources') }
    }

    def "provides exclude dir information"() {

        file('build.gradle').text = """
apply plugin: 'java'
apply plugin: 'idea'

idea.module.excludeDirs += file('foo')
"""

        when:
        IdeaProject project = loadToolingModel(IdeaProject)
        def module = project.children[0]

        then:
        module.contentRoots[0].excludeDirectories.any { it.path.endsWith 'foo' }
    }

    def "provides dependencies"() {

        def fakeRepo = file("repo")

        def dependency = new MavenFileRepository(fakeRepo).module("foo.bar", "coolLib", "1.0")
        dependency.artifact(classifier: 'sources')
        dependency.artifact(classifier: 'javadoc')
        dependency.publish()

        projectDir.file("gradle.properties") << """
            org.gradle.parallel=$parallel
        """
        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    repositories {
        maven { url "${fakeRepo.toURI()}" }
    }

    dependencies {
        ${implementationConfiguration} project(':api')
        ${testImplementationConfiguration} 'foo.bar:coolLib:1.0'
    }

    idea.module.downloadJavadoc = true
}
"""
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = loadToolingModel(IdeaProject)
        def module = project.children.find { it.name == 'impl' }

        then:
        def libs = module.dependencies
        IdeaSingleEntryLibraryDependency lib = libs.find {it instanceof IdeaSingleEntryLibraryDependency}

        lib.file.exists()
        lib.file.path.endsWith('coolLib-1.0.jar')

        lib.source.exists()
        lib.source.path.endsWith('coolLib-1.0-sources.jar')

        lib.javadoc.exists()
        lib.javadoc.path.endsWith('coolLib-1.0-javadoc.jar')

        lib.scope.scope == 'TEST'

        IdeaModuleDependency mod = libs.find {it instanceof IdeaModuleDependency}
        mod.targetModuleName == 'api'
        if (targetVersion >= GradleVersion.version("3.4")) {
            mod.scope.scope == 'PROVIDED'
        } else {
            mod.scope.scope == 'COMPILE'
        }

        where:
        parallel << [true, false]
    }

    def "module has access to gradle project and its tasks"() {

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
        IdeaProject project = loadToolingModel(IdeaProject)

        then:
        def impl = project.modules.find { it.name == 'impl'}
        def root = project.modules.find { it.name == 'root'}

        root.gradleProject.tasks.find { it.name == 'rootTask' && it.path == ':rootTask' && it.project == root.gradleProject }
        !root.gradleProject.tasks.find { it.name == 'implTask' }

        impl.gradleProject.tasks.find { it.name == 'implTask' && it.path == ':impl:implTask' && it.project == impl.gradleProject}
        !impl.gradleProject.tasks.find { it.name == 'rootTask' }
    }

    def "offline model should not resolve external dependencies"() {

        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    dependencies {
        ${implementationConfiguration} project(':api')
        ${testImplementationConfiguration} 'i.dont:Exist:2.4'
    }
}
"""
        settingsFile.text = "include 'api', 'impl'"

        when:
        BasicIdeaProject project = withConnection { connection -> connection.getModel(BasicIdeaProject.class) }
        def impl = project.children.find { it.name == 'impl' }

        then:
        def libs = impl.dependencies
        if (targetVersion >= GradleVersion.version("3.4")) {
            libs.size() == 3
        } else {
            libs.size() == 1
        }
        libs.each {
            it.targetModuleName == 'api'
        }
    }
}
