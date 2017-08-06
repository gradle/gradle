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
import org.gradle.util.VersionNumber
import org.junit.Assume

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORIES

class TwirlCompileIntegrationTest extends PlayMultiVersionIntegrationTest {

    def destinationDir = file("build/src/play/binary/twirlTemplatesScalaSources/views")

    def setup() {
        settingsFile << """ rootProject.name = 'twirl-play-app' """
        buildFile << """
            plugins {
                id 'play-application'
            }

            ${PLAY_REPOSITORIES}

            model {
                components {
                    play {
                        targetPlatform "play-${version}"
                    }
                }
            }
        """
    }

    def "can run TwirlCompile with html template"() {
        given:
        withTwirlTemplate()
        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("html/index.template.scala")

        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        skipped(":compilePlayBinaryPlayTwirlTemplates")
    }

    def "can run TwirlCompile with javascript template"() {
        given:
        twirlTemplate("test.scala.js") << """
            @(jsFile: String, payload: String)
            
            (function() {
            @*
             * Inject the PAYLOAD
             *@
            var PAYLOAD = @JavaScript(payload);
            
            @*
             * Inject the jsFile
             *@
            @JavaScript(jsFile)
            }());
        """
        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("js/test.template.scala")

        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        skipped(":compilePlayBinaryPlayTwirlTemplates")
    }

    def "can run TwirlCompile with xml template"() {
        given:
        twirlTemplate("test.scala.xml") << """
            @(jsFile: String, payload: String)
            
            (function() {
            @*
             * Inject the PAYLOAD
             *@
            var PAYLOAD = @JavaScript(payload);
            
            @*
             * Inject the jsFile
             *@
            @JavaScript(jsFile)
            }());
        """
        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("xml/test.template.scala")

        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        skipped(":compilePlayBinaryPlayTwirlTemplates")
    }

    def "can run TwirlCompile with txt template"() {
        given:
        twirlTemplate("test.scala.txt") << """
            @(jsFile: String, payload: String)
            
            (function() {
            @*
             * Inject the PAYLOAD
             *@
            var PAYLOAD = @JavaScript(payload);
            
            @*
             * Inject the jsFile
             *@
            @JavaScript(jsFile)
            }());
        """
        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("txt/test.template.scala")

        when:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        skipped(":compilePlayBinaryPlayTwirlTemplates")
    }

    def "runs compiler incrementally"() {
        when:
        withTwirlTemplate("input1.scala.html")
        then:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("html/input1.template.scala")
        def input1FirstCompileSnapshot = destinationDir.file("html/input1.template.scala").snapshot()

        when:
        withTwirlTemplate("input2.scala.html")
        and:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        then:
        destinationDir.assertHasDescendants("html/input1.template.scala", "html/input2.template.scala")
        and:
        destinationDir.file("html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)

        when:
        file("app/views/input2.scala.html").delete()
        then:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("html/input1.template.scala")
    }

    def "removes stale output files in incremental compile"(){
        given:
        withTwirlTemplate("input1.scala.html")
        withTwirlTemplate("input2.scala.html")
        succeeds("compilePlayBinaryPlayTwirlTemplates")

        and:
        destinationDir.assertHasDescendants("html/input1.template.scala", "html/input2.template.scala")
        def input1FirstCompileSnapshot = destinationDir.file("html/input1.template.scala").snapshot()

        when:
        file("app/views/input2.scala.html").delete()

        then:
        succeeds("compilePlayBinaryPlayTwirlTemplates")
        and:
        destinationDir.assertHasDescendants("html/input1.template.scala")
        destinationDir.file("html/input1.template.scala").assertHasNotChangedSince(input1FirstCompileSnapshot)
        destinationDir.file("html/input2.template.scala").assertDoesNotExist()
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
                ":compilePlayBinaryPlayTwirlTemplates",
                ":compilePlayBinaryPlayExtraTwirl",
                ":compilePlayBinaryPlayOtherTwirl"
        )

        and:
        destinationDir.assertHasDescendants("html/index.template.scala")
        file("build/src/play/binary/otherTwirlScalaSources").assertHasDescendants("templates/html/other.template.scala")
        file("build/src/play/binary/extraTwirlScalaSources").assertHasDescendants("html/extra.template.scala")

        and:
        jar("build/playBinary/lib/twirl-play-app.jar")
            .containsDescendants("views/html/index.class", "templates/html/other.class", "html/extra.class")
    }

    def "can build twirl source set with default Java imports" () {
        Assume.assumeTrue(versionNumber < VersionNumber.parse("2.6.2"))
        withTwirlJavaSourceSets()
        withTemplateSourceExpectingJavaImports(file("twirlJava", "javaTemplate.scala.html"))
        validateThatPlayJavaDependencyIsAdded()

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compilePlayBinaryPlayTwirlJava"

        and:
        jar("build/playBinary/lib/twirl-play-app.jar")
            .containsDescendants("html/javaTemplate.class")
    }

    def "can build twirl source sets both with and without default Java imports" () {
        Assume.assumeTrue(versionNumber < VersionNumber.parse("2.6.2"))
        withTwirlJavaSourceSets()
        withTemplateSource(file("app", "views", "index.scala.html"))
        withTemplateSourceExpectingJavaImports(file("twirlJava", "javaTemplate.scala.html"))

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(
            ":compilePlayBinaryPlayTwirlTemplates",
            ":compilePlayBinaryPlayTwirlJava"
        )

        and:
        jar("build/playBinary/lib/twirl-play-app.jar")
            .containsDescendants("html/javaTemplate.class", "views/html/index.class")
    }

    def "twirl source sets default to Scala imports" () {
        withTemplateSource(file("app", "views", "index.scala.html"))
        validateThatPlayJavaDependencyIsNotAdded()
        validateThatSourceSetsDefaultToScalaImports()

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compilePlayBinaryPlayTwirlTemplates"
    }

    def "extra sources appear in the component report"() {
        withExtraSourceSets()

        when:
        succeeds "components"

        then:
        output.contains """
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
        includes: **/*.scala.html, **/*.scala.js, **/*.scala.xml, **/*.scala.txt

Binaries
"""

    }

    def withTemplateSource(File templateFile) {
        templateFile << """@(message: String)

            <h1>@message</h1>

        """
    }

    def twirlTemplate(String fileName) {
        file("app", "views", fileName)
    }

    def withTwirlTemplate(String fileName = "index.scala.html") {
        def templateFile = file("app", "views", fileName)
        templateFile.createFile()
        withTemplateSource(templateFile)
    }

    def withTemplateSourceExpectingJavaImports(File templateFile) {
        templateFile << """
            <!DOCTYPE html>
            <html>
                <body>
                  <p>@UUID.randomUUID().toString()</p>
                </body>
            </html>
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

    def withTwirlJavaSourceSets() {
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            twirlJava(TwirlSourceSet) {
                                defaultImports = TwirlImports.JAVA
                                source.srcDir "twirlJava"
                            }
                        }
                    }
                }
            }
        """
    }

    def validateThatPlayJavaDependencyIsAdded() {
        validateThatPlayJavaDependency(true)
    }

    def validateThatPlayJavaDependencyIsNotAdded() {
        validateThatPlayJavaDependency(false)
    }

    def validateThatPlayJavaDependency(boolean shouldBePresent) {
        buildFile << """
            model {
                components {
                    play {
                        binaries.all { binary ->
                            tasks.withType(TwirlCompile) {
                                doFirst {
                                    assert ${shouldBePresent ? "" : "!"} configurations.play.dependencies.any {
                                        it.group == "com.typesafe.play" &&
                                        it.name == "play-java_\${binary.targetPlatform.scalaPlatform.scalaCompatibilityVersion}" &&
                                        it.version == binary.targetPlatform.playVersion
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
    }

    def validateThatSourceSetsDefaultToScalaImports() {
        buildFile << """
            model {
                components {
                    play {
                        binaries.all { binary ->
                            tasks.withType(TwirlCompile) {
                                doFirst {
                                    assert defaultImports == TwirlImports.SCALA
                                    assert binary.inputs.withType(TwirlSourceSet).every {
                                        it.defaultImports == TwirlImports.SCALA
                                    }
                                }
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
