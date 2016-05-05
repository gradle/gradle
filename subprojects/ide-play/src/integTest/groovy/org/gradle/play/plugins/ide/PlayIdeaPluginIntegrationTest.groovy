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

import org.gradle.play.integtest.fixtures.PlayApp
import org.gradle.play.integtest.fixtures.app.BasicPlayApp
import org.gradle.plugins.ide.idea.IdeaModuleFixture

import static org.gradle.plugins.ide.idea.IdeaFixtures.parseIml
import static org.gradle.plugins.ide.idea.IdeaFixtures.parseIpr

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

        parseIml(moduleFile).dependencies.dependencies.any {
            if (it instanceof IdeaModuleFixture.ImlLibrary) {
                return it.name.startsWith("scala-sdk") && it.level == "project"
            }
            false
        }

        def libraryTable = parseIpr(projectFile).libraryTable
        def scalaSdk = libraryTable.library.find { it.@name.toString().startsWith("scala-sdk") && it.@type == "Scala" }
        def scalaClasspath = scalaSdk.properties."compiler-classpath".root."@url"
        scalaClasspath.size() == 104
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

    def "IDEA metadata contains correct dependencies for RUNTIME, COMPILE, TEST"() {
        applyIdePlugin()
        succeeds("assemble") // Need generated directories to exist
        when:
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

    def "when model configuration changes, IDEA metadata can be rebuilt"() {
        applyIdePlugin()
        succeeds(ideTask)
        when:
        file("other-assets").mkdirs()
        buildFile << """
model {
    components {
        play {
            binaries.all {
                assets.addAssetDir file("other-assets")
            }
        }
    }
}
"""
        and:
        succeeds(ideTask)
        then:
        result.assertTaskNotSkipped(":ideaModule")
        def content = parseIml(moduleFile).content
        content.assertContainsSourcePaths("other-assets", "public", "conf", "app", "test", "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources")
    }

    def "IDEA metadata contains custom source set"() {
        applyIdePlugin()
        file("extra/java").mkdirs()
        buildFile << """
model {
    components {
        play {
            sources {
                extraJava(JavaSourceSet) {
                    source.srcDir "extra/java"
                }
            }
        }
    }
}
"""
        when:
        succeeds(ideTask)
        then:
        def content = parseIml(moduleFile).content
        content.assertContainsSourcePaths("extra/java", "public", "conf", "app", "test", "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources")
    }

    def "can generate IDEA metadata with custom source set"() {
        applyIdePlugin()
        when:
        file("generated-assets").mkdirs()
        buildFile << """
class GenerateAssets extends DefaultTask {
    @OutputDirectory
    File destinationDir

    @TaskAction
    void generateAssets() {
        [ "a", "b", "c" ].each { filename ->
            File outputFile = new File(destinationDir, filename)
            outputFile.text = filename
        }
    }
}

model {
    components {
        play {
            binaries.all { binary ->
                tasks.create("generate\${binary.name.capitalize()}Assets", GenerateAssets) { task ->
                    destinationDir = project.file("generated-assets")
                    binary.assets.addAssetDir destinationDir
                    binary.assets.builtBy task
                }
            }
        }
    }
}
"""
        and:
        succeeds(ideTask)
        then:
        result.assertTasksExecuted(":compilePlayBinaryPlayRoutes", ":compilePlayBinaryPlayTwirlTemplates", ":generateBinaryAssets", ":ideaProject", ":ideaModule", ":ideaWorkspace", ":idea")
        def content = parseIml(moduleFile).content
        content.assertContainsSourcePaths("generated-assets", "public", "conf", "app", "test", "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources")
    }

    def "IDEA plugin depends on source generation tasks"() {
        applyIdePlugin()

        when:
        succeeds(ideTask)
        then:
        result.assertTasksExecuted(":compilePlayBinaryPlayRoutes", ":compilePlayBinaryPlayTwirlTemplates", ":ideaProject", ":ideaModule", ":ideaWorkspace", ":idea")
    }
}
