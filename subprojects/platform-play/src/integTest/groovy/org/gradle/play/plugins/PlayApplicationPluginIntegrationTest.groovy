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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.play.integtest.fixtures.Repositories.PLAY_REPOSITORIES

@Requires(TestPrecondition.JDK8_OR_LATER)
class PlayApplicationPluginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << """ rootProject.name = 'play-app' """
        buildFile << """
            plugins {
                id 'play-application'
            }

            ${PLAY_REPOSITORIES}
        """
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
        skipped(":compilePlayBinaryScala")
        notExecuted(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates")

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
        output.contains """
    Java source 'play:extraJava'
        srcDir: src${File.separator}extraJava
"""
        output.contains """
    Scala source 'play:extraScala'
        srcDir: src${File.separator}extraScala
"""

        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryScala",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary",
                ":assemble")
        notExecuted(
                ":compilePlayBinaryPlayRoutes",
                ":compilePlayBinaryPlayTwirlTemplates")

        and:
        jar("build/playBinary/lib/play-app.jar").hasDescendants("org/acme/model/JavaPerson.class", "org/acme/model/ScalaPerson.class")
        jar("build/playBinary/lib/play-app-assets.jar").hasDescendants()
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }
}
