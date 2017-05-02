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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

class ArtifactTransformParallelIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()

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
                List<File> transform(File input) {
                    new URL('${server.uri}' + input.name).text
                    if (input.name.startsWith("bad")) {
                        throw new RuntimeException("Transform Failure: " + input.name)
                    }
                    def output = new File(outputDirectory, input.name + ".txt")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
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
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    assert artifacts.artifactFiles.collect { it.name } == ['test-1.3.jar.txt', 'test2-2.3.jar.txt', 'test3-3.3.jar.txt']
                }
            }
        """

        server.expectConcurrentExecution("test-1.3.jar", "test2-2.3.jar", "test3-3.3.jar")

        when:
        succeeds ":resolve"

        then:
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
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'c.jar.txt']
                }
            }
        """

        server.expectConcurrentExecution("a.jar", "b.jar", "c.jar")

        when:
        succeeds ":resolve"

        then:
        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
        outputContains("Transforming c.jar to c.jar.txt")
    }

    def "transformations are applied in parallel for a mix of external and file dependency artifacts"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"
        def m3 = mavenRepo.module("test", "test3", "3.3").publish()
        m3.artifactFile.text = "12"

        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            def c = file('c.jar')
            c.text = '123'

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile files([a, b])
                compile 'test:test:1.3'
                compile files(c)
                compile 'test:test2:2.3'
                compile 'test:test3:3.3'
            }
            task resolve {
                doLast {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'c.jar.txt', 'test-1.3.jar.txt', 'test2-2.3.jar.txt', 'test3-3.3.jar.txt']
                }
            }
        """

        server.expectConcurrentExecution("a.jar", "b.jar", "c.jar", "test-1.3.jar", "test2-2.3.jar", "test3-3.3.jar")

        when:
        executer.withArguments("--max-workers=6")
        succeeds ":resolve"

        then:
        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
    }

    def "files are transformed as soon as they are downloaded"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"

        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            def c = file('c.jar')
            c.text = '123'

            repositories {
                maven { url "${server.uri}" }
            }
            dependencies {
                compile files([a, b])
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }
            task resolve {
                doLast {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'test-1.3.jar.txt', 'test2-2.3.jar.txt']
                }
            }
        """

        server.expectSerialExecution(server.file(m1.pom.path, m1.pom.file))
        server.expectSerialExecution(server.file(m2.pom.path, m2.pom.file))

        def handle = server.blockOnConcurrentExecutionAnyOfToResources(4, [
            server.resource("a.jar"),
            server.resource("b.jar"),
            server.file(m1.artifact.path, m1.artifact.file),
            server.file(m2.artifact.path, m2.artifact.file),
        ])
        def transform1 = server.blockOnConcurrentExecutionAnyOfToResources(1, [server.resource("test-1.3.jar")])
        server.expectSerialExecution(server.resource("test2-2.3.jar"))

        when:
        def build = executer.withArguments("--max-workers=4").withTasks(':resolve').start()

        // 4 concurrent operations -> both artifacts are being downloaded and both local files are being transformed
        handle.waitForAllPendingCalls()

        // Complete one of the downloads and one of the local files
        handle.release("a.jar")
        handle.release(m1.artifact.path)

        // Download has completed, transforming the result. Other artifact is still downloading
        transform1.waitForAllPendingCalls()
        transform1.releaseAll()

        handle.releaseAll()

        def result = build.waitForFinish()

        then:
        result.assertOutputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        result.assertOutputContains("Transforming a.jar to a.jar.txt")
        result.assertOutputContains("Transforming b.jar to b.jar.txt")
    }

    def "failures are collected from transformations applied parallel"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('bad-b.jar')
            b.text = '12'
            def c = file('bad-c.jar')
            c.text = '123'

            dependencies {
                compile files([a, b])
                compile files(c)
            }
            task resolve {
                doLast {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    println artifacts.artifactFiles.collect { it.name }
                }
            }
        """

        server.expectConcurrentExecution("a.jar", "bad-b.jar", "bad-c.jar")

        when:
        fails ":resolve"

        then:
        failure.assertHasCause("Failed to transform file 'bad-b.jar' to match attributes {artifactType=size} using transform SynchronizedTransform")
        failure.assertHasCause("Failed to transform file 'bad-c.jar' to match attributes {artifactType=size} using transform SynchronizedTransform")
    }
}
