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

class TwirlCompileIntegrationTest extends PlayMultiVersionIntegrationTest {

    def setup() {
        settingsFile << """ rootProject.name = 'twirl-play-app' """
        buildFile << """
            plugins {
                id 'play-application'
            }

            repositories{
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
            }

            model {
                components {
                    play {
                        targetPlatform "play-${version}"
                    }
                }
            }
        """
    }

    def "can run TwirlCompile"(){
        given:
        withCustomCompileTask()
        withTwirlTemplate()
        when:
        succeeds("twirlCompile")
        then:
        file("build/twirl/views/html/index.template.scala").exists()

        when:
        succeeds("twirlCompile")
        then:
        skipped(":twirlCompile");
    }

    def "runs compiler incrementally"(){
        when:
        withCustomCompileTask()
        withTwirlTemplate("input1.scala.html")
        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
        def input1FirstCompileSnapshot = file("build/twirl/views/html/input1.template.scala").snapshot();

        when:
        withTwirlTemplate("input2.scala.html")
        and:
        succeeds("twirlCompile")
        then:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala", "input2.template.scala")
        and:
        file("build/twirl/views/html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)

        when:
        file("app/views/input2.scala.html").delete()
        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        withCustomCompileTask()
        withTwirlTemplate("input1.scala.html")
        withTwirlTemplate("input2.scala.html")
        succeeds("twirlCompile")

        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala", "input2.template.scala")
        def input1FirstCompileSnapshot = file("build/twirl/views/html/input1.template.scala").snapshot();

        when:
        file("app/views/input2.scala.html").delete()

        then:
        succeeds("twirlCompile")
        and:
        file("build/twirl/views/html").assertHasDescendants("input1.template.scala")
        file("build/twirl/views/html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot);
        file("build/twirl/views/html/input2.template.scala").assertDoesNotExist()
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
                ":twirlCompileTwirlTemplatesPlayBinary",
                ":twirlCompileExtraTwirlPlayBinary",
                ":twirlCompileOtherTwirlPlayBinary"
        )

        and:
        file("build/playBinary/src/twirlCompileTwirlTemplatesPlayBinary/views/html/").assertHasDescendants("index.template.scala")
        file("build/playBinary/src/twirlCompileOtherTwirlPlayBinary/templates/html").assertHasDescendants("other.template.scala")
        file("build/playBinary/src/twirlCompileExtraTwirlPlayBinary/html").assertHasDescendants("extra.template.scala")

        and:
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("views/html/index.class")
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("templates/html/other.class")
        jar("build/playBinary/lib/twirl-play-app.jar").assertContainsFile("html/extra.class")
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
    Scala source 'play:appSources'
        app
    Twirl template source 'play:extraTwirl'
        extraSources
    Twirl template source 'play:otherTwirl'
        otherSources
    JVM resources 'play:resources'
        conf
    Twirl template source 'play:twirlTemplates'
        app

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
        buildFile << """
            model{
                tasks.twirlCompile{
                    source '${templateFile.toURI()}'
                }
            }"""

    }

    def withCustomCompileTask() {
        buildFile << """
            model {
                tasks {
                    create("twirlCompile", TwirlCompile){ task ->
                        task.outputDirectory = file('build/twirl')
                        task.source file('./app')
                        task.platform = binaries.playBinary.targetPlatform
                    }
                }
            }
        """
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
