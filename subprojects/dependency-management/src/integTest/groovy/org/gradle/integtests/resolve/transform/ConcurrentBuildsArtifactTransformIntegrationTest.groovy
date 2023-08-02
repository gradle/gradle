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

class ConcurrentBuildsArtifactTransformIntegrationTest extends AbstractDependencyResolutionTest {

    @Override
    def setupBuildOperationFixture() {
        //disable because of a test that is incompatible with the build operation fixture
    }

    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
        buildFile << """
enum Color { Red, Green, Blue }
def type = Attribute.of("artifactType", String)

abstract class ToColor implements TransformAction<Parameters> {
    interface Parameters extends TransformParameters {
        @Input
        Property<Color> getColor()
    }

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        def color = parameters.color.get()
        println "Transforming \$input.name to \$color"
        def out = outputs.file(color.toString())
        out.text = input.name
    }
}

dependencies {
    registerTransform(ToColor) {
        from.attribute(type, "jar")
        to.attribute(type, "red")
        parameters {
            color = Color.Red
        }
    }
    registerTransform(ToColor) {
        from.attribute(type, "jar")
        to.attribute(type, "blue")
        parameters {
            color = Color.Blue
        }
    }
}

configurations {
    compile
}
dependencies {
    def f = file("thing.jar")
    f.text = "not-really-a-jar"
    compile files(f)
}

task redThings {
    def files = configurations.compile.incoming.artifactView {
        attributes {
            attribute(type, "red")
        }
    }.files
    doLast {
        files*.name
    }
}

task blueThings {
    def files = configurations.compile.incoming.artifactView {
        attributes {
            attribute(type, "blue")
        }
    }.files
    doLast {
        files*.name
    }
}
"""
    }

    def "multiple build processes share transform output cache"() {
        given:
        // Run two builds where one build applies one transform and the other build the second
        buildFile << """
task block1 {
    doLast {
        ${server.callFromBuild("block1")}
    }
}
block1.mustRunAfter redThings
blueThings.mustRunAfter block1

task block2 {
    doLast {
        ${server.callFromBuild("block2")}
    }
}
block2.mustRunAfter blueThings

"""
        // Ensure build scripts compiled
        run("help")

        def block1 = server.expectAndBlock("block1")
        def block2 = server.expectAndBlock("block2")

        when:
        // Block until first build has produced red things
        def build1 = executer.withTasks("redThings", "block1", "blueThings").start()
        block1.waitForAllPendingCalls()

        // Block until second build has produced blue things
        def build2 = executer.withTasks("redThings", "blueThings", "block2").start()
        block2.waitForAllPendingCalls()

        // Finish up first build while second build is still running
        block1.releaseAll()
        def result1 = build1.waitForFinish()

        block2.releaseAll()
        def result2 = build2.waitForFinish()

        then:
        result1.output.count("Transforming") == 1
        result1.output.count("Transforming thing.jar to Red") == 1
        result2.output.count("Transforming") == 1
        result2.output.count("Transforming thing.jar to Blue") == 1
    }

    def "file is transformed once only by concurrent builds"() {
        given:
        // Run two builds concurrently
        buildFile << """
task block1 {
    doLast {
        ${server.callFromBuild("block1")}
    }
}
redThings.mustRunAfter block1

task block2 {
    doLast {
        ${server.callFromBuild("block2")}
    }
}
redThings.mustRunAfter block2
"""
        // Ensure build scripts compiled
        run("help")

        def block = server.expectConcurrentAndBlock("block1", "block2")

        when:
        // Block until both builds are ready to start resolving
        def build1 = executer.withTasks("block1", "redThings", "blueThings").start()
        def build2 = executer.withTasks("block2", "redThings", "blueThings").start()

        // Resolve concurrently
        block.waitForAllPendingCalls()
        block.releaseAll()

        def result1 = build1.waitForFinish()
        def result2 = build2.waitForFinish()

        then:
        result1.output.count("Transforming") + result2.output.count("Transforming") == 2
        result1.output.count("Transforming thing.jar to Red") + result2.output.count("Transforming thing.jar to Red") == 1
        result1.output.count("Transforming thing.jar to Blue") + result2.output.count("Transforming thing.jar to Blue") == 1
    }
}
