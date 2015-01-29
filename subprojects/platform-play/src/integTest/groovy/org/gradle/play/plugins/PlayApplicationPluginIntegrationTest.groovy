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
    Scala source 'play:appSources'
        app

Binaries
    Play Application Jar 'playBinary'
        build using task: :playBinary
        platform: Play Platform (Play 2.3.7, Scala: 2.11, Java: Java SE ${JavaVersion.current().majorVersion})
        tool chain: Default Play Toolchain"""))
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
        failure.assertHasDescription("Exception thrown while executing model rule: org.gradle.play.plugins.PlayApplicationPlugin\$Rules#failOnMultiplePlayComponents(org.gradle.model.collection.CollectionBuilder<org.gradle.play.PlayApplicationSpec>)")
        failure.assertHasCause("Multiple components of type 'PlayApplicationSpec' are not supported.")
    }

    def "builds empty play binary when no sources present"() {
        when:
        succeeds("assemble")

        then:
        executedAndNotSkipped(":createPlayBinaryJar", ":createPlayBinaryAssetsJar", ":playBinary", ":assemble")
        skipped(":routesCompilePlayBinary" , ":twirlCompilePlayBinary", ":scalaCompilePlayBinary")

        and:
        jar("build/playBinary/lib/play-app.jar").hasDescendants()
        jar("build/playBinary/lib/play-app-assets.jar").hasDescendants()
    }

    JarTestFixture jar(String fileName) {
           new JarTestFixture(file(fileName))
    }
}