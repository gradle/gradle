/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.maven.MavenPom

/**
 * Tests {@link org.gradle.api.distribution.plugins.DistributionPlugin}.
 */
@TestReproducibleArchives
class DistributionPluginIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getMainTask() {
        return "distZip"
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'TestProject'
        """

        file("someFile").createFile()
    }

    def "can publish distribution"() {
        when:
        buildFile << """
            plugins {
                id("distribution")
                id("maven-publish")
            }

            group = "org.acme"
            version = "1.0"

            distributions {
                main {
                    contents {
                        from { "someFile" }
                    }
                }
            }

            configurations {
                distribution {
                    attributes {
                        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, "distribution"))
                    }
                    outgoing.artifact(distZip)
                }
            }

            interface MyServices {
                @Inject
                SoftwareComponentFactory getSoftwareComponentFactory()
            }

            def factory = objects.newInstance(MyServices).softwareComponentFactory
            def distributionComponent = factory.adhoc("distribution")
            distributionComponent.addVariantsFromConfiguration(configurations.distribution) {}

            publishing {
                repositories {
                    maven { url = uri("${file("repo").toURI()}") }
                }
                publications {
                    maven(MavenPublication) {
                       from distributionComponent
                    }
                }
            }
        """

        then:
        succeeds("publishMavenPublicationToMavenRepository")
        file("repo/org/acme/TestProject/1.0/TestProject-1.0.zip").assertIsFile()

        and:
        def pom = new MavenPom(file("repo/org/acme/TestProject/1.0/TestProject-1.0.pom"))
        pom.groupId == "org.acme"
        pom.artifactId == "TestProject"
        pom.version == "1.0"
        pom.packaging == "zip"
    }

    def createDistributionWithoutVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        and:
        buildFile << """
            plugins {
                id("distribution")
            }

            distributions {
                main {
                    distributionBaseName = 'myDistribution'
                }
            }
        """

        when:
        run('distZip')

        then:
        file('build/distributions/myDistribution.zip').exists()
    }

    def assembleAllDistribution() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        and:
        buildFile << """
            plugins {
                id("distribution")
            }

            distributions {
                main {
                    distributionBaseName = 'myDistribution'
                }
            }
        """

        when:
        run('assemble')

        then:
        file('build/distributions/myDistribution.zip').exists()
        file('build/distributions/myDistribution.tar').exists()
    }

    def createDistributionWithVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        and:
        buildFile << """
            plugins {
                id("distribution")
            }

            version = '1.2'
            distributions {
                main {
                    distributionBaseName = 'myDistribution'
                }
            }
            distZip{

            }
        """

        when:
        run('distZip')

        then:
        file('build/distributions/myDistribution-1.2.zip').exists()
    }

    def createDistributionWithoutBaseNameAndVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        and:
        buildFile << """
            plugins {
                id("distribution")
            }
        """

        when:
        run('distZip')

        then:
        file('build/distributions/TestProject.zip').exists()
    }

    def createDistributionWithoutBaseNameAndWithVersion() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }

        and:
        buildFile << """
            plugins {
                id("distribution")
            }

            version = 1.2
        """

        when:
        run('distZip')

        then:
        file('build/distributions/TestProject-1.2.zip').exists()
    }

    def "can create distribution with .tar in project name"() {
        when:
        buildFile << """
            plugins {
                id("application")
            }

            application {
                mainClass = "Main"
            }
        """

        file("src/main/java/Main.java") << "public class Main {}"
        settingsFile << """
            rootProject.name = 'projectWithtarInName'
        """

        then:
        succeeds("distTar")
        new TarTestFixture(file("build/distributions/projectWithtarInName.tar")).assertContainsFile("projectWithtarInName/lib/projectWithtarInName.jar")
    }

    def "uses custom classifier in archive names and install locations"() {
        when:
        buildFile << """
            plugins {
                id("distribution")
            }

            distributions {
                main {
                    distributionClassifier.set("foo")
                }
                customOne {
                    distributionBaseName.set("custom")
                    distributionClassifier.set("bar")
                }
                customTwo {
                    distributionBaseName.set("custom")
                    distributionClassifier.set("baz")
                }
                all {
                    contents.from("someFile")
                }
            }
        """

        file("someFile") << "some text"

        then:
        succeeds(
            "assembleDist", "assembleCustomOneDist", "assembleCustomTwoDist",
            "installDist", "installCustomOneDist", "installCustomTwoDist"
        )
        file('build/distributions/TestProject-foo.zip').assertIsFile()
        file('build/distributions/custom-bar.zip').assertIsFile()
        file('build/distributions/custom-baz.zip').assertIsFile()
        file('build/distributions/TestProject-foo.tar').assertIsFile()
        file('build/distributions/custom-bar.tar').assertIsFile()
        file('build/distributions/custom-baz.tar').assertIsFile()

        file('build/install/TestProject-foo').assertIsDir()
        file('build/install/custom-bar').assertIsDir()
        file('build/install/custom-baz').assertIsDir()
    }
}
