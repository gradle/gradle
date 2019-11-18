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

package org.gradle.play.plugins.ide

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.plugins.ide.fixtures.IdeaModuleFixture

import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIml
import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIpr

abstract class PlayIdeaPluginIntegrationTest extends PlayIdePluginIntegrationTest {

    String getIdePlugin() {
        "idea"
    }

    String getIdeTask() {
        "idea"
    }

    File getModuleFile() {
        file("${playApp.name}.iml")
    }

    File getProjectFile() {
        file("${playApp.name}.ipr")
    }

    File getWorkspaceFile() {
        file("${playApp.name}.iws")
    }

    List<File> getIdeFiles() {
        [moduleFile,
         projectFile,
         workspaceFile]
    }

    abstract String[] getSourcePaths()
    abstract int getExpectedScalaClasspathSize()

    @ToBeFixedForInstantExecution
    def "IML contains path to Play app sources"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        def content = parseIml(moduleFile).content
        content.assertContainsSourcePaths(sourcePaths)
        content.assertContainsExcludes("build", ".gradle")
    }

    @ToBeFixedForInstantExecution
    def "IDEA metadata contains correct Scala version"() {
        applyIdePlugin()
        buildFile << """
    class Rules extends RuleSource {
        @Validate
        public void assertJavaVersion(@Path("tasks.ideaModule") GenerateIdeaModule ideaModule,
                                      @Path("binaries.playBinary") PlayApplicationBinarySpec playBinary) {
            assert ideaModule.module.scalaPlatform == playBinary.targetPlatform.scalaPlatform
            println "Validated Scala Version"
        }
    }

    allprojects {
        pluginManager.withPlugin("play") {
            apply plugin: Rules
        }
    }
"""
        when:
        succeeds(ideTask)
        then:
        outputContains("Validated Scala Version")

        parseIml(moduleFile).dependencies.dependencies.any {
            if (it instanceof IdeaModuleFixture.ImlLibrary) {
                return it.name.startsWith("scala-sdk") && it.level == "project"
            }
            false
        }

        def libraryTable = parseIpr(projectFile).libraryTable
        def scalaSdk = libraryTable.library.find { it.@name.toString().startsWith("scala-sdk") && it.@type == "Scala" }
        def scalaClasspath = scalaSdk.properties."compiler-classpath".root."@url"
        scalaClasspath.size() == expectedScalaClasspathSize
    }

    @ToBeFixedForInstantExecution
    def "IDEA metadata contains correct Java version"() {
        applyIdePlugin()
        buildFile << """
    class Rules extends RuleSource {
        @Validate
        public void assertJavaVersion(@Path("tasks.ideaModule") GenerateIdeaModule ideaModule,
                                      @Path("binaries.playBinary") PlayApplicationBinarySpec playBinary) {
            assert ideaModule.module.targetBytecodeVersion == playBinary.targetPlatform.javaPlatform.targetCompatibility
            assert ideaModule.module.languageLevel == new org.gradle.plugins.ide.idea.model.IdeaLanguageLevel(playBinary.targetPlatform.javaPlatform.targetCompatibility)
            println "Validated Java Version"
        }
    }

    allprojects {
        pluginManager.withPlugin("play") {
            apply plugin: Rules
        }
    }
"""
        when:
        succeeds(ideTask)
        then:
        outputContains("Validated Java Version")
    }

    @ToBeFixedForInstantExecution
    def "IDEA metadata contains correct dependencies for RUNTIME, COMPILE, TEST"() {
        applyIdePlugin()
        succeeds("assemble") // Need generated directories to exist
        when:
        executer.noDeprecationChecks()
        succeeds(ideTask)
        then:

        def externalLibs = parseIml(moduleFile).dependencies.libraries
        def compileDeps = externalLibs.findAll({ it.scope == "COMPILE" }).collect { it.url }
        compileDeps.any {
            it.endsWith("build/playBinary/classes")
        }

        def runtimeDeps = externalLibs.findAll({ it.scope == "RUNTIME" })
        !runtimeDeps.empty

        def testDeps = externalLibs.findAll({ it.scope == "TEST" })
        !testDeps.empty
    }

    @ToBeFixedForInstantExecution
    def "IDEA plugin depends on source generation tasks"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        result.assertTasksExecuted(buildTasks)
    }
}
