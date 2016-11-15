/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ArtifactTransformIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        buildFile << """
allprojects {
    configurations {
        compile {
            attributes usage: 'api'
        }
    }
}

@TransformInput(format = 'jar')
class FileSizer extends ArtifactTransform {
    private File output

    @TransformOutput(format = 'size')
    File getOutput() {
        return output
    }

    void transform(File input) {
        output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input} to \${output}"
        output.text = String.valueOf(input.length())
    }
}

"""
    }

    def "applies transforms to artifacts for external dependencies"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"

        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }

            ${fileSizeConfigurationAndTransform()}
        """

        when:
        succeeds "resolve"

        then:
        file("build/libs").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs/test-1.3.jar.txt").text == "4"
        file("build/libs/test2-2.3.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/transformed/test-1.3.jar.txt").text == "4"
        file("build/transformed/test2-2.3.jar.txt").text == "2"
    }

    def "applies transforms to files from file dependencies"() {
        when:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            
            dependencies {
                compile files(a, b)
            }

            ${fileSizeConfigurationAndTransform()}
        """

        succeeds "resolve"

        then:
        file("build/libs").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/libs/a.jar.txt").text == "4"
        file("build/libs/b.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/transformed/a.jar.txt").text == "4"
        file("build/transformed/b.jar.txt").text == "2"
    }

    // Documents existing behaviour, not desired behaviour
    def "removes artifacts and files with format that does not match requested from the result"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """
        buildFile << """
            project(':lib') {
                artifacts {
                    compile file('lib.jar')
                    compile file('lib.classes')
                    compile file('lib')
                }
            }

            project(':app') {
                configurations {
                    compile {
                        format = 'jar'
                    }
                }

                dependencies {
                    compile project(':lib')
                }

                task resolve {
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ['lib.jar']
                    }
                }
            }
        """

        expect:
        succeeds "resolve"
    }

    def "applies transforms to artifacts from project dependencies"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    archiveName = 'lib2.jar'
                }

                artifacts {
                    compile jar1, jar2
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${fileSizeConfigurationAndTransform()}
            }
        """

        when:
        succeeds "resolve"

        then:
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/libs/lib1.jar.txt").text == file("app/build/lib1.jar").length() as String
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/transformed/lib1.jar.txt").text == file("app/build/lib1.jar").length() as String
    }

    def fileSizeConfigurationAndTransform() {
        """
            configurations {
                compile {
                    format = 'size'
                    resolutionStrategy.registerTransform(FileSizer) {
                        outputDirectory = project.file("\${buildDir}/transformed")
                    }
                }
            }

            task resolve(type: Copy) {
                dependsOn configurations.compile
                from configurations.compile.incoming.artifacts*.file
                into "\${buildDir}/libs"
            }
"""
    }
}
