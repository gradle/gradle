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

import groovy.transform.NotYetImplemented
import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp

import static org.gradle.plugins.ide.idea.IdeaFixtures.parseIml

class PlayIdeaPluginIntegrationTest extends PlayIdePluginIntegrationTest {

    @Override
    PlayApp getPlayApp() {
        new BasicPlayApp()
    }

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

    def "IML contains path to Play app sources"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        def content = parseIml(moduleFile).content
        content.assertContainsSourcePaths("public", "conf", "app", "test", "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources")
        content.assertContainsExcludes("build", ".gradle")

    }

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

    apply plugin: Rules
"""
        when:
        succeeds(ideTask)
        then:
        result.output.contains("Validated Scala Version")
        // TODO: Check that IML contains a scala-library-XXX
        // TODO: Check that IPR contains a scala-library-XXX
    }

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

    apply plugin: Rules
"""
        when:
        succeeds(ideTask)
        then:
        result.output.contains("Validated Java Version")
    }

    @NotYetImplemented
    def "IML contains path to generated sources"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        false
    }

    @NotYetImplemented
    def "IDEA metadata contains correct dependencies for RUNTIME, COMPILE, TEST"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        false
    }

    @NotYetImplemented
    def "when model configuration changes, IDEA metadata can be rebuilt"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        false
    }

    @NotYetImplemented
    def "IDEA metadata contains custom source set"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        false
    }

    def "IDEA plugin depends on source generation tasks"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        result.assertTasksExecuted(":compilePlayBinaryPlayRoutes", ":compilePlayBinaryPlayTwirlTemplates", ":ideaProject", ":ideaModule", ":ideaWorkspace", ":idea")
    }
}
