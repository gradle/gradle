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

class CrashingBuildsArtifactTransformIntegrationTest extends AbstractDependencyResolutionTest {
    def "cleans up cached output after build process crashes during transform"() {
        given:
        buildFile << """

enum Color { Red, Green, Blue }
def type = Attribute.of("artifactType", String)

abstract class ToColor implements TransformAction<Parameters> {
    interface Parameters extends TransformParameters {
        @Input
        Property<Color> getColor()

        @Input
        Property<Boolean> getBroken()
    }

    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        def input = inputArtifact.get().asFile
        def color = parameters.color.get()
        def one = outputs.file("one")
        def outputDirectory = one.parentFile
        assert outputDirectory.directory && outputDirectory.list().length == 0
        println "Transforming \$input.name to \$color"
        one.text = "one"
        // maybe killed here
        if (parameters.broken.get()) {
            Runtime.runtime.halt(1)
        }
        def two = outputs.file("two")
        two.text = "two"
    }
}

dependencies {
    registerTransform(ToColor) {
        from.attribute(type, "jar")
        to.attribute(type, "red")
        parameters {
            color = Color.Red
            broken = providers.systemProperty("crash").map { true }.orElse(false)
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
    def fileCollection = configurations.compile.incoming.artifactView { attributes { it.attribute(type, "red") } }.files
    doLast {
        fileCollection.files
    }
}
"""
        // Ensure build scripts compiled
        run("help")

        when:
        def build1 = executer.withTasks("redThings").withArgument("-Dcrash=true").start()
        build1.waitForFailure()

        run("redThings")

        then:
        output.count("Transforming") == 1
        output.count("Transforming thing.jar to Red") == 1
    }
}
