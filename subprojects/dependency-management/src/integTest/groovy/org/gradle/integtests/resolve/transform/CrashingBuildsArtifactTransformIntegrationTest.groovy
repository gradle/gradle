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

class CrashingBuildsArtifactTransformIntegrationTest extends AbstractDependencyResolutionTest {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()

    def "cleans up cached output after build process crashes during transform"() {
        given:
        buildFile << """

enum Color { Red, Green, Blue }
def color = Attribute.of("color", Color)

class ToColor extends ArtifactTransform {
    Color color

    ToColor(Color color) { this.color = color }

    List<File> transform(File input) {
        assert outputDirectory.directory && outputDirectory.list().length == 0
        println "Transforming \$input.name to \$color"
        def one = new File(outputDirectory, "one")
        one.text = "one"
        // maybe killed here
        new URL("$server.uri").text
        def two = new File(outputDirectory, "two")
        two.text = "two"
        [one, two]
    }
}

dependencies {
    attributesSchema.attribute(color)
    registerTransform {
        to.attribute(color, Color.Red)
        artifactTransform(ToColor) { params(Color.Red) }
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
        configurations.compile.incoming.artifactView().attributes { it.attribute(color, Color.Red) }.files.files
    }
}
"""
        // Ensure build scripts compiled
        run("help")

        when:
        def build1 = executer.withTasks("redThings").start()
        server.waitFor()
        build1.abort()
        server.release()

        def build2 = executer.withTasks("redThings").start()
        server.sync()
        def result = build2.waitForFinish()

        then:
        result.output.count("Transforming") == 1
        result.output.count("Transforming thing.jar to Red") == 1
    }
}
