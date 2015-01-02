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
        buildFile << """
            plugins {
                id 'play'
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

    def "adds project version to archive name and base directory" () {
        buildFile << """
            version = "1.0"
        """

        when:
        succeeds "dist"

        then:
        zip("build/distributions/playBinary-1.0.zip").containsDescendants("playBinary-1.0/lib/play.jar")
    }

    def "can add an additional arbitrary distribution" () {
        buildFile << """
            model {
                distributions {
                    myDist {
                        baseName = "mySpecialDist"
                        contents {
                            from binaries.playBinary.tasks.withType(org.gradle.jvm.tasks.Jar)
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
        succeeds "dist"

        then:
        executedAndNotSkipped(
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":createMyDistDist")

        and:
        zip("build/distributions/mySpecialDist.zip").containsDescendants(
                "mySpecialDist/play.jar",
                "mySpecialDist/play-assets.jar",
                "mySpecialDist/txt/additionalFile.txt")

        when:
        succeeds "stage"

        then:
        [ "play.jar",
          "play-assets.jar",
          "txt/additionalFile.txt"
        ].each { fileName ->
            file("build/stage/myDist/${fileName}").exists()
        }
    }

    ZipTestFixture zip(String path) {
        return new ZipTestFixture(file(path))
    }
}
