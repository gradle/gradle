/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactTransformParallelIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """

        buildFile << """
            def artifactType = Attribute.of('artifactType', String)
                
            dependencies {
                registerTransform {
                    from.attribute(artifactType, "jar")
                    to.attribute(artifactType, "size")
                    artifactTransform(SynchronizedTransform)
                }
            }
            configurations {
                compile
            }
            
            
            class SynchronizedTransform extends ArtifactTransform {
                static cyclicBarrier = new java.util.concurrent.CyclicBarrier(3, {
                    println "BARRIER HIT"
                })

                List<File> transform(File input) {
                    def output = new File(outputDirectory, input.name + ".txt")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    cyclicBarrier.await()
                    return [output]
                }
            }
"""
    }

    def "transformations are applied in parallel for each external dependency artifact"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"
        def m3 = mavenRepo.module("test", "test3", "3.3").publish()
        m3.artifactFile.text = "12"

        given:

        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
                compile 'test:test3:3.3'
            }
            task resolve {
                doLast {
                    def artifacts = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts
                    println "files: " + artifacts.artifactFiles.collect { it.name }
                }
            }
        """

        when:
        succeeds ":resolve"

        then:
        outputContains("BARRIER HIT")

        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming test2-2.3.jar to test2-2.3.jar.txt")
        outputContains("Transforming test3-3.3.jar to test3-3.3.jar.txt")
    }

    def "transformations are applied in parallel for each file dependency artifact"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            def c = file('c.jar')
            c.text = '123'

            dependencies {
                compile files([a, b])
                compile files(c)
            }
            task resolve {
                doLast {
                    def artifacts = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.files
                    println "files: " + artifacts.collect { it.name }
                }
            }
        """

        when:
        succeeds ":resolve"

        then:
        outputContains("BARRIER HIT")

        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
        outputContains("Transforming c.jar to c.jar.txt")
    }
}
