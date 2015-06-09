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

package org.gradle.play.plugins

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.TextUtil
import org.junit.Rule

class PlayApplicationPluginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << """ rootProject.name = 'play-app' """
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
            ivy {
                url "https://repo.typesafe.com/typesafe/ivy-releases/"
                layout "pattern", {
                    ivy "[organisation]/[module]/[revision]/ivys/ivy.xml"
                    artifact "[organisation]/[module]/[revision]/jars/[artifact].[ext]"
                }
            }
        }
"""
    }

    def "can register PlayApplicationSpec component"() {
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
    Twirl template source 'play:twirlTemplates'
        srcDir: app
        includes: **/*.html

Binaries
    Play Application Jar 'playBinary'
        build using task: :playBinary
        platform: Play Platform (Play ${DefaultPlayPlatform.DEFAULT_PLAY_VERSION}, Scala: 2.11, Java: Java SE ${JavaVersion.current().majorVersion})"""))
    }

    def "cannot register multiple PlayApplicationSpec components"() {
        given:
        buildFile << """
        model {
             components {
                 myOtherApp(PlayApplicationSpec)
             }
        }
"""
        when:
        fails "components"

        then:
        failure.assertHasDescription("A problem occurred configuring root project 'play-app'.")
        failure.assertHasCause("Multiple components of type 'PlayApplicationSpec' are not supported.")
    }

    def "builds empty play binary when no sources present"() {
        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":assemble")
        skipped(":compilePlayBinaryRoutes",
                ":compilePlayBinaryTwirlTemplates",
                ":compilePlayBinaryScala")

        and:
        jar("build/playBinary/lib/play-app.jar").hasDescendants()
        jar("build/playBinary/lib/play-app-assets.jar").hasDescendants()
    }

    def "can declare additional scala and java sourceSets"() {
        given:
        buildFile << """
        model {
            components {
                play {
                    sources {
                        extraJava(JavaSourceSet) {
                            source.srcDir "src/extraJava"
                        }
                        extraScala(ScalaLanguageSourceSet) {
                            source.srcDir "src/extraScala"
                        }
                    }
                }
            }
        }
"""
        and:
        file("src/extraJava/org/acme/model/JavaPerson.java") << """
            package org.acme.model;
            class JavaPerson {}
"""
        file("src/extraScala/org/acme/model/ScalaPerson.scala") << """
            package org.acme.model;
            class ScalaPerson {}
"""

        when:
        succeeds("components")

        then:
        output.contains(TextUtil.toPlatformLineSeparators("""
    Java source 'play:extraJava'
        srcDir: src${File.separator}extraJava
"""))
        output.contains(TextUtil.toPlatformLineSeparators("""
    Scala source 'play:extraScala'
        srcDir: src${File.separator}extraScala
"""))

        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":assemble")
        skipped(":compilePlayBinaryRoutes",
                ":compilePlayBinaryTwirlTemplates")

        and:
        jar("build/playBinary/lib/play-app.jar").hasDescendants("org/acme/model/JavaPerson.class", "org/acme/model/ScalaPerson.class")
        jar("build/playBinary/lib/play-app-assets.jar").hasDescendants()
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
