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
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.junit.Rule

class PlayDistributionPluginIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        buildFile << """
            plugins {
                id 'play-application'
                id 'play-distribution'
            }

            repositories {
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
            }
        """
    }

    def "builds empty distribution when no sources present" () {
        buildFile << """
            configurations { playDeps }
            dependencies { playDeps "com.typesafe.play:play_2.11:2.3.7" }

            model {
                tasks.createPlayBinaryStartScripts {
                    doLast {
                        assert classpath.files.containsAll(configurations.playDeps.files)
                    }
                }
                tasks.createPlayBinaryDist {
                    doLast {
                        assert zipTree(archivePath).collect { it.name }.containsAll(configurations.playDeps.collect { it.name })
                    }
                }
            }
        """

        when:
        succeeds "dist"

        then:
        executedAndNotSkipped(
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":createPlayBinaryStartScripts",
                ":createPlayBinaryDist")
        skipped(
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":scalaCompilePlayBinary")

        and:
        zip("build/distributions/playBinary.zip").containsDescendants(
                "playBinary/lib/play.jar",
                "playBinary/lib/play-assets.jar",
                "playBinary/bin/playBinary",
                "playBinary/bin/playBinary.bat"
        )

        when:
        succeeds "stage"

        then:
        executedAndNotSkipped(":stagePlayBinaryDist")
        skipped(
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":createPlayBinaryStartScripts",
                ":routesCompilePlayBinary",
                ":twirlCompilePlayBinary",
                ":scalaCompilePlayBinary")

        and:
        [ "playBinary/lib/play.jar",
          "playBinary/lib/play-assets.jar",
          "playBinary/bin/playBinary",
          "playBinary/bin/playBinary.bat"
        ].each { fileName ->
            assert file("build/stage/${fileName}").exists()
        }
    }

    ZipTestFixture zip(String path) {
        return new ZipTestFixture(file(path))
    }
}
