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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.ZipTestFixture

class DistributionZipIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """ rootProject.name = 'dist-play-app' """
        buildFile << """
            plugins {
                id 'play'
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

    def "can add to default distribution" () {
        buildFile << """
            model {
                distributions {
                    playBinary {
                        contents {
                            from "additionalFile.txt"
                        }
                    }
                }
            }
        """
        file("additionalFile.txt").createFile()

        when:
        succeeds "dist"

        then:
        zip("build/distributions/playBinary.zip").containsDescendants("playBinary/additionalFile.txt")
    }

    def "can add an additional arbitrary distribution" () {
        buildFile << """
            model {
                distributions {
                    myDist {
                        baseName = "mySpecialDist"
                        contents {
                            binary = binaries.playBinary
                            into("txt") {
                                from "additionalFile.txt"
                            }
                        }
                    }
                }
            }
        """
        file("additionalFile.txt").createFile()

        when:
        succeeds "stage"

        then:
        file("build/stage/mySpecialDist").assertContainsDescendants(
                "lib/dist-play-app.jar",
                "lib/dist-play-app-assets.jar",
                "txt/additionalFile.txt"
        )

        when:
        succeeds "dist"

        then:
        executedAndNotSkipped(
                ":createMyDistDist")

        and:
        zip("build/distributions/mySpecialDist.zip").containsDescendants(
                "mySpecialDist/lib/dist-play-app.jar",
                "mySpecialDist/lib/dist-play-app-assets.jar",
                "mySpecialDist/txt/additionalFile.txt")
    }

    def "produces sensible error when distribution has no binary" () {
        buildFile << """
            model {
                distributions {
                    myDist {
                        baseName = "mySpecialDist"
                        contents {
                            into("txt") {
                                from "additionalFile.txt"
                            }
                        }
                    }
                }
            }
        """
        file("additionalFile.txt").createFile()

        when:
        fails "stage"

        then:
        failureDescriptionContains("Exception thrown while executing model rule: org.gradle.play.plugins.PlayDistributionPlugin#createDistributionContentTasks(org.gradle.model.collection.CollectionBuilder<org.gradle.api.Task>, java.io.File, org.gradle.play.distribution.PlayDistributionContainer, org.gradle.play.plugins.PlayPluginConfigurations)")
        failureHasCause("Play Distribution 'myDist' does not have a configured Play binary.")
    }

    ZipTestFixture zip(String path) {
        return new ZipTestFixture(file(path))
    }
}
