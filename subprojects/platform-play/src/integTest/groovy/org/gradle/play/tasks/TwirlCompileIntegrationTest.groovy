/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.tasks

import org.gradle.play.integtest.fixtures.PlayMultiVersionIntegrationTest
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.TextUtil
import static org.gradle.play.integtest.fixtures.Repositories.*

class TwirlCompileIntegrationTest extends PlayMultiVersionIntegrationTest {
    def destinationDirPath = "build/playBinary/src/compilePlayBinaryTwirlTemplates/views/html"
    def destinationDir = file(destinationDirPath)

    def setup() {
        settingsFile << """ rootProject.name = 'twirl-play-app' """
        buildFile << """
            plugins {
                id 'play-application'
            }

            ${PLAY_REPOSITORES}

            model {
                components {
                    play {
                        targetPlatform "play-${version}"
                    }
                }
            }
        """
    }

    def "can run TwirlCompile"() {
        given:
        withTwirlTemplate()
        when:
        succeeds("compilePlayBinaryTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("index.template.scala")

        when:
        succeeds("compilePlayBinaryTwirlTemplates")
        then:
        skipped(":compilePlayBinaryTwirlTemplates");
    }

    def "runs compiler incrementally"() {
        when:
        withTwirlTemplate("input1.scala.html")
        then:
        succeeds("compilePlayBinaryTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
        def input1FirstCompileSnapshot = file("${destinationDirPath}/input1.template.scala").snapshot();

        when:
        withTwirlTemplate("input2.scala.html")
        and:
        succeeds("compilePlayBinaryTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("input1.template.scala", "input2.template.scala")
        and:
        file("${destinationDirPath}/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)

        when:
        file("app/views/input2.scala.html").delete()
        then:
        succeeds("compilePlayBinaryTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        withTwirlTemplate("input1.scala.html")
        withTwirlTemplate("input2.scala.html")
        succeeds("compilePlayBinaryTwirlTemplates")

        and:
        destinationDir.assertHasDescendants("input1.template.scala", "input2.template.scala")
        def input1FirstCompileSnapshot = file("${destinationDirPath}/input1.template.scala").snapshot();

        when:
        file("app/views/input2.scala.html").delete()

        then:
        succeeds("compilePlayBinaryTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("input1.template.scala")
        file("${destinationDirPath}/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot);
        file("${destinationDirPath}/input2.template.scala").assertDoesNotExist()
    }

    def "builds multiple twirl source sets as part of play build" () {
        withExtraSourceSets()
        withTemplateSource(file("app", "views", "index.scala.html"))
        withTemplateSource(file("otherSources", "templates", "other.scala.html"))
        withTemplateSource(file("extraSources", "extra.scala.html"))

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryTwirlTemplates",
                ":compilePlayBinaryExtraTwirl",
                ":compilePlayBinaryOtherTwirl"
        )

        and:
        destinationDir.assertHasDescendants("index.template.scala")
        file("build/playBinary/src/compilePlayBinaryOtherTwirl/templates/html").assertHasDescendants("other.template.scala")
        file("build/playBinary/src/compilePlayBinaryExtraTwirl/html").assertHasDescendants("extra.template.scala")

        and:
        jar("build/playBinary/lib/twirl-play-app.jar")
            .containsDescendants("views/html/index.class", "templates/html/other.class", "html/extra.class")
    }

    def "extra sources appear in the component report"() {
        withExtraSourceSets()

        when:
        succeeds "components"

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
Play Application 'play'
-----------------------

Source sets
    Java source 'play:java'
        srcDir: app
        includes: **/*.java
    JVM resources 'play:resources'
        srcDir: conf
    Routes source 'play:routes'
        srcDir: conf
        includes: routes, *.routes
    Scala source 'play:scala'
        srcDir: app
        includes: **/*.scala
    Twirl template source 'play:extraTwirl'
        srcDir: extraSources
    Twirl template source 'play:otherTwirl'
        srcDir: otherSources
    Twirl template source 'play:twirlTemplates'
        srcDir: app
        includes: **/*.html

Binaries
"""))

    }


    def withTemplateSource(File templateFile) {
        templateFile << """@(message: String)

    @play20.welcome(message)

"""
    }

    def withTwirlTemplate(String fileName = "index.scala.html") {
        def templateFile = file("app", "views", fileName)
        templateFile.createFile()
        withTemplateSource(templateFile)
    }

    def withExtraSourceSets() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraTwirl(TwirlSourceSet) {
                                source.srcDir "extraSources"
                            }
                            otherTwirl(TwirlSourceSet) {
                                source.srcDir "otherSources"
                            }
                        }
                    }
                }
            }
        """
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
