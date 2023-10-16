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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Ignore

class ArtifactTransformParallelIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer(180_000)

    def setup() {
        server.start()

        setupBuild(new BuildTestFile(testDirectory, "root"))

        executer.beforeExecute {
            withArgument("--max-workers=10")
        }
    }

    private void setupBuild(BuildTestFile buildTestFile) {
        buildTestFile.with {
            settingsFile << """
                rootProject.name = '${rootProjectName}'
            """
            buildFile << """
                def usage = Attribute.of('usage', String)
                def artifactType = Attribute.of('artifactType', String)

                allprojects {
                    dependencies {
                        attributesSchema {
                            attribute(usage)
                        }
                    }
                    configurations {
                        compile {
                            attributes.attribute usage, 'api'
                        }
                    }
                    dependencies {
                        registerTransform(SynchronizedTransform) {
                            from.attribute(artifactType, "jar")
                            to.attribute(artifactType, "size")
                        }
                    }
                    configurations {
                        compile
                    }
                }

                import org.gradle.api.artifacts.transform.TransformParameters

                abstract class SynchronizedTransform implements TransformAction<TransformParameters.None> {
                    @InputArtifact
                    abstract Provider<FileSystemLocation> getInputArtifact()

                    void transform(TransformOutputs outputs) {
                        def input = inputArtifact.get().asFile
                        ${server.callFromBuildUsingExpression("input.name")}
                        if (input.name.startsWith("bad")) {
                            throw new RuntimeException("Transform Failure: " + input.name)
                        }
                        if (!input.exists()) {
                            throw new IllegalStateException("Input file \${input} does not exist")
                        }
                        def output = outputs.file(input.name + ".txt")
                        println "Transforming \${input.name} to \${output.name}"
                        output.text = String.valueOf(input.length())
                    }
                }
            """
        }
    }

    def "transformations are applied in parallel for each external dependency artifact"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"
        def m3 = mavenRepo.module("test", "test3", "3.3").publish()
        m3.artifactFile.text = "123"

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
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files artifacts.artifactFiles

                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ['test-1.3.jar.txt', 'test2-2.3.jar.txt', 'test3-3.3.jar.txt']
                }
            }
        """

        server.expectConcurrent("test-1.3.jar", "test2-2.3.jar", "test3-3.3.jar")

        when:
        succeeds ":resolve"

        then:
        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming test2-2.3.jar to test2-2.3.jar.txt")
        outputContains("Transforming test3-3.3.jar to test3-3.3.jar.txt")
    }

    def "transformations are applied in parallel for project artifacts"() {
        given:
        createDirs("lib1", "lib2", "lib3")
        settingsFile << """
            include "lib1", "lib2", "lib3"
        """

        buildFile << """
            configure([project(":lib1"), project(":lib2"), project(":lib3")]) {

                task jar(type: Jar) {
                    archiveFileName = "\${project.name}.jar"
                    destinationDirectory = buildDir
                }
                artifacts {
                    compile jar
                }
            }

            dependencies {
                compile project(":lib1")
                compile project(":lib2")
                compile project(":lib3")
            }
            task resolve {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files(artifacts.artifactFiles)

                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ["lib1.jar.txt", "lib2.jar.txt", "lib3.jar.txt"]
                }
            }
        """

        server.expectConcurrent("lib1.jar", "lib2.jar", "lib3.jar")

        when:
        succeeds ":resolve"

        then:
        outputContains("Transforming lib1.jar to lib1.jar.txt")
        outputContains("Transforming lib2.jar to lib2.jar.txt")
        outputContains("Transforming lib3.jar to lib3.jar.txt")
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
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files(artifacts.artifactFiles)

                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'c.jar.txt']
                }
            }
        """

        server.expectConcurrent("a.jar", "b.jar", "c.jar")

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
        m3.artifactFile.text = "123"

        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '12345'
            def b = file('b.jar')
            b.text = '124'
            def c = file('c.jar')
            c.text = '1236'

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
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files(artifacts.artifactFiles)

                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'c.jar.txt', 'test-1.3.jar.txt', 'test2-2.3.jar.txt', 'test3-3.3.jar.txt']
                }
            }
        """

        server.expectConcurrent("a.jar", "b.jar", "c.jar", "test-1.3.jar", "test2-2.3.jar", "test3-3.3.jar")

        when:
        succeeds ":resolve"

        then:
        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
    }

    @ToBeFixedForConfigurationCache(because = "Files are downloaded when cache entry is written")
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
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ['a.jar.txt', 'b.jar.txt', 'test-1.3.jar.txt', 'test2-2.3.jar.txt']
                }
            }
        """

        server.expectConcurrent(
            server.get(m1.pom.path).sendFile(m1.pom.file),
            server.get(m2.pom.path).sendFile(m2.pom.file))

        def handle = server.expectConcurrentAndBlock(
            server.get("a.jar"),
            server.get("b.jar"),
            server.get(m1.artifact.path).sendFile(m1.artifact.file),
            server.get(m2.artifact.path).sendFile(m2.artifact.file))
        def transform1 = server.expectAndBlock(server.get("test-1.3.jar"))
        server.expect(server.get("test2-2.3.jar"))

        when:
        def build = executer.withTasks(':resolve').start()

        // 4 concurrent operations -> both artifacts are being downloaded and both local files are being transformed
        handle.waitForAllPendingCalls()

        // Complete one of the downloads and one of the local files
        handle.release("a.jar")
        handle.release(m1.artifact.path)

        // Download has completed, transforming the result. Other artifact is still downloading
        transform1.waitForAllPendingCalls()
        transform1.releaseAll()

        handle.releaseAll()

        result = build.waitForFinish()

        then:
        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
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
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files(artifacts.artifactFiles)
                outputs.upToDateWhen { false }

                doLast {
                    println artifacts.artifactFiles.collect { it.name }
                }
            }
        """

        server.expectConcurrent("a.jar", "bad-b.jar", "bad-c.jar")

        when:
        fails ":resolve"

        then:
        failure.assertHasCause("Failed to transform bad-b.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Failed to transform bad-c.jar to match attributes {artifactType=size}")
    }

    def "only one transformer execution per workspace"() {

        createDirs("lib", "app1", "app2")
        settingsFile << """
            include "lib", "app1", "app2"
        """

        buildFile << """
            project(":lib") {
                dependencies {
                    compile files("lib1.jar")
                }

                task jar2(type: Jar) {
                    archiveFileName = 'lib2.jar'
                    destinationDirectory = buildDir
                }
                artifacts {
                    compile jar2
                }
            }

            configure([project("app1"), project("app2")]) {
                dependencies {
                    compile project(":lib")
                }
                task resolve {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    inputs.files(artifacts.artifactFiles)

                    doLast {
                        println artifacts.artifactFiles.collect { it.name }
                    }
                }
            }
        """
        file("lib/lib1.jar") << "some content"

        server.expect("lib2.jar")
        server.expect("lib1.jar")

        when:
        def handle = executer.withArguments("--parallel").withTasks("app1:resolve", "app2:resolve").start()
        then:
        handle.waitForFinish()
    }

    def "only one process should run immutable transform for same artifact at once"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"

        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
            }
            task resolve {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files artifacts.artifactFiles

                doLast {
                    assert artifacts.artifactFiles.collect { it.name } == ['test-1.3.jar.txt']
                }
            }
        """
        server.expect("test-1.3.jar")

        when:
        def buildHandles = (1..2).collect {
            return executer.withTasks(':resolve').start()
        }

        then:
        buildHandles[0].each {
            it.waitForFinish()
            assert it.standardOutput.contains("Transforming test-1.3.jar to test-1.3.jar.txt")
        }
        buildHandles[1].each {
            it.waitForFinish()
            assert !it.standardOutput.contains("Transforming test-1.3.jar to test-1.3.jar.txt")
        }
    }

    @Ignore("Needs a fix for parallel artifact transform")
    def "only one process can run immutable transforms at the same time"() {
        given:
        List<BuildTestFile> builds = (1..3).collect { idx ->
            def lib = mavenRepo.module("org.test.foo", "build${idx}").publish()
            def build = new BuildTestFile(file("build${idx}"), "build${idx}")
            setupBuild(build)
            build.with {
                def toBeTransformed = file(build.rootProjectName + ".jar")
                toBeTransformed.text = '1234'
                buildFile << """
                    repositories {
                        maven { url '${mavenRepo.uri}' }
                    }

                    dependencies {
                        compile '${lib.groupId}:${lib.artifactId}:${lib.version}'
                    }

                    task beforeResolve {
                        def projectName = project.name
                        doLast {
                            ${server.callFromBuildUsingExpression('"resolveStarted_" + projectName')}
                        }
                    }

                    task resolve {
                        def artifacts = configurations.compile.incoming.artifactView {
                            attributes { it.attribute(artifactType, 'size') }
                        }.artifacts
                        inputs.files(artifacts.artifactFiles)

                        dependsOn(beforeResolve)

                        def projectName = project.name
                        doLast {
                            assert artifacts.artifactFiles.collect { it.name } == [projectName + '-1.0.jar.txt']
                        }
                    }
                """
            }
            return build
        }
        def buildNames = builds*.rootProjectName

        expect:
        server.expectConcurrent(buildNames.collect { "resolveStarted_" + it })
        def transformations = server.expectConcurrentAndBlock(1, buildNames.collect { it + "-1.0.jar" } as String[])
        def buildHandles = builds.collect {
            executer.inDirectory(it).withTasks("resolve").start()
        }

        for (build in builds) {
            transformations.waitForAllPendingCalls()
            Thread.sleep(1000)
            transformations.release(1)
        }

        buildHandles.each {
            it.waitForFinish()
        }
    }
}
