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
import org.gradle.play.internal.platform.PlayMajorVersion

import static org.gradle.plugins.ide.fixtures.IdeaFixtures.parseIml

class PlayIdeaPluginBasicIntegrationTest extends PlayIdeaPluginIntegrationTest {
    static final Map PLAY_VERSION_TO_CLASSPATH_SIZE = [(PlayMajorVersion.PLAY_2_2_X): 99,
                                                       (PlayMajorVersion.PLAY_2_3_X): 102,
                                                       (PlayMajorVersion.PLAY_2_4_X): 96,
                                                       (PlayMajorVersion.PLAY_2_5_X): 105,
                                                       (PlayMajorVersion.PLAY_2_6_X): 111]

    @Override
    PlayApp getPlayApp() {
        new BasicPlayApp(oldVersion: isOldVersion())
    }

    String[] getSourcePaths() {
        ["public", "conf", "app", "test", "build/src/play/binary/routesScalaSources", "build/src/play/binary/twirlTemplatesScalaSources"]
    }

    String[] getBuildTasks() {
        [":compilePlayBinaryPlayRoutes", ":compilePlayBinaryPlayTwirlTemplates", ":ideaProject", ":ideaModule", ":ideaWorkspace", ":idea"]
    }

    int getExpectedScalaClasspathSize() {
        return PLAY_VERSION_TO_CLASSPATH_SIZE[PlayMajorVersion.forPlayVersion(version.toString())]
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

}
