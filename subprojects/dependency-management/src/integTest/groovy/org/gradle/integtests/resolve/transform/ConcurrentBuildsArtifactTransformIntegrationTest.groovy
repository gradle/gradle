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
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class ConcurrentBuildsArtifactTransformIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule CyclicBarrierHttpServer server1 = new CyclicBarrierHttpServer()
    @Rule CyclicBarrierHttpServer server2 = new CyclicBarrierHttpServer()

    def setup() {
        buildFile << """

enum Color { Red, Green, Blue }
def color = Attribute.of("color", Color)

class ToColor extends ArtifactTransform {
    Color color

    @javax.inject.Inject
    ToColor(Color color) { this.color = color }

    List<File> transform(File input) { 
        println "Transforming \$input.name to \$color"
        def out = new File(outputDirectory, color.toString())
        out.text = input.name
        [out]
    }
}

dependencies {
    attributesSchema.attribute(color)
    registerTransform {
        to.attribute(color, Color.Red)
        artifactTransform(ToColor) { params(Color.Red) }
    }
    registerTransform {
        to.attribute(color, Color.Blue)
        artifactTransform(ToColor) { params(Color.Blue) }
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
    doLast {
        configurations.compile.incoming.artifactView {
            attributes { it.attribute(color, Color.Red) }
        }.files.files
    }
}

task blueThings {
    doLast {
        configurations.compile.incoming.artifactView {
            attributes { it.attribute(color, Color.Blue) }
        }.files.files
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
        new URL("$server1.uri").text
    }
}
block1.mustRunAfter redThings
blueThings.mustRunAfter block1

task block2 {
    doLast {
        new URL("$server2.uri").text
    }
}
block2.mustRunAfter blueThings

"""
        // Ensure build scripts compiled
        run("help")

        when:
        // Block until first build has produced red things
        def build1 = executer.withTasks("redThings", "block1", "blueThings").start()
        def server1WaitForResult = server1.waitFor(false, 120)

        // Block until second build has produced blue things
        def build2 = executer.withTasks("redThings", "blueThings", "block2").start()
        def server2WaitForResult = server2.waitFor(false, 120)

        // Finish up first build while second build is still running
        server1.release()
        def result1 = build1.waitForFinish()

        server2.release()
        def result2 = build2.waitForFinish()

        then:
        server1WaitForResult && server2WaitForResult

        and:
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
        new URL("$server1.uri").text
    }
}
redThings.mustRunAfter block1

task block2 {
    doLast {
        new URL("$server2.uri").text
    }
}
redThings.mustRunAfter block2
"""
        // Ensure build scripts compiled
        run("help")

        when:
        // Block until both builds are ready to start resolving
        def build1 = executer.withTasks("block1", "redThings", "blueThings").start()
        def server1WaitForResult = server1.waitFor(false, 120)

        def build2 = executer.withTasks("block2", "redThings", "blueThings").start()
        def server2WaitForResult = server2.waitFor(false, 120)

        // Resolve concurrently
        server1.release()
        server2.release()

        def result1 = build1.waitForFinish()
        def result2 = build2.waitForFinish()

        then:
        server1WaitForResult && server2WaitForResult

        and:
        result1.output.count("Transforming") + result2.output.count("Transforming") == 2
        result1.output.count("Transforming thing.jar to Red") + result2.output.count("Transforming thing.jar to Red") == 1
        result1.output.count("Transforming thing.jar to Blue") + result2.output.count("Transforming thing.jar to Blue") == 1
    }
}
