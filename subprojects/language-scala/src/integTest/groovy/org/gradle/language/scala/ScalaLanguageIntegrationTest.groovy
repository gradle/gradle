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

package org.gradle.language.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.language.scala.fixtures.BadScalaLibrary
import org.gradle.language.scala.fixtures.TestScalaComponent
import org.gradle.test.fixtures.archive.JarTestFixture

class ScalaLanguageIntegrationTest extends AbstractIntegrationSpec {

    def app = new TestScalaComponent()

    def setup() {
        buildFile << """
        plugins {
            id 'jvm-component'
            id 'scala-lang'
        }
        repositories{
            mavenCentral()
        }
    """
    }

    def "can build binary with sources in conventional location"() {
        when:
        app.sources*.writeToDir(file("src/myLib/scala"))
        app.resources*.writeToDir(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }

"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLibScala", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(expectedOutputs)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "generated binary includes compiled classes from all java source sets"() {
        when:
        def source1 = app.sources[0]
        def source2 = app.sources[1]

        source1.writeToDir(file("src/myLib/scala"))
        source2.writeToDir(file("src/myLib/extraScala"))

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    extraScala(ScalaLanguageSourceSet)
                }
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":compileMyLibJarMyLibScala", ":compileMyLibJarMyLibExtraScala", ":createMyLibJar", ":myLibJar"

        and:
        file("build/classes/myLibJar").assertHasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)

        and:
        def jar = jarFile("build/jars/myLibJar/myLib.jar")
        jar.hasDescendants(source1.classFile.fullPath, source2.classFile.fullPath)
    }

    def "can configure source locations for scala and resource source sets"() {
        when:
        app.sources*.writeToDir(file("src/myLib/myScala"))
        app.resources*.writeToDir(file("src/myLib/myResources"))

        // Conventional locations are ignore with explicit configuration
        file("src/myLib/scala/Ignored.scala") << "IGNORE ME"
        file("src/myLib/resources/Ignored.txt") << "IGNORE ME"

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    scala {
                        source.srcDir "src/myLib/myScala"
                    }
                    resources {
                        source.srcDir "src/myLib/myResources"
                    }
                }
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        file("build/classes/myLibJar").assertHasDescendants(app.expectedOutputs*.fullPath as String[])
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(app.expectedOutputs*.fullPath as String[])

    }

    def "can combine resources and sources in a single source directory"() {
        when:
        app.sources*.writeToDir(file("src/myLib"))
        app.resources*.writeToDir(file("src/myLib"))
        String[] expectedOutputs = [app.sources[0].classFile.fullPath, app.sources[1].classFile.fullPath, app.resources[0].fullPath, app.resources[1].fullPath]

        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec) {
                sources {
                    scala.source {
                        srcDir "src/myLib"
                        exclude "**/*.txt"
                    }
                    resources.source {
                        srcDir "src/myLib"
                        exclude "**/*.scala"
                    }
                }
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        file("build/classes/myLibJar").assertHasDescendants(expectedOutputs)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "can configure output directories for classes and resources"() {
        when:
        app.sources*.writeToDir(file("src/myLib/scala"))
        app.resources*.writeToDir(file("src/myLib/resources"))
        def expectedOutputs = app.expectedOutputs*.fullPath as String[]

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
        jvm {
            allBinaries {
                classesDir = file("\${project.buildDir}/custom-classes")
                resourcesDir = file("\${project.buildDir}/custom-resources")
            }
        }
    }
"""
        and:
        succeeds "assemble"

        then:
        executedAndNotSkipped ":processMyLibJarMyLibResources", ":compileMyLibJarMyLibScala", ":createMyLibJar", ":myLibJar"

        and:
        file("build/custom-classes").assertHasDescendants(app.sources*.classFile.fullPath as String[])
        file("build/custom-resources").assertHasDescendants(app.resources*.fullPath as String[])

        and:
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(expectedOutputs)
    }

    def "reports failure to compile bad scala sources"() {
        when:
        def badApp = new BadScalaLibrary()
        badApp.sources*.writeToDir(file("src/myLib/scala"))

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }
"""
        then:
        fails "assemble"

        and:
        badApp.compilerErrors.each {
            assert errorOutput.contains(it)
        }
    }

    private JarTestFixture jarFile(String s) {
        new JarTestFixture(file(s))
    }

}
