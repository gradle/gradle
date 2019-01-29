/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

trait ArtifactTransformTestFixture {
    abstract TestFile getBuildFile()

    /**
     * Each project produces 'blue' variants, and has a task that resolves the 'green' variant.
     * Caller will need to register transforms that produce 'green' from 'blue'
     */
    void setupBuildWithColorAttributes() {
        buildFile << """
import ${javax.inject.Inject.name}

def color = Attribute.of('color', String)
allprojects {
    configurations {
        implementation {
            attributes.attribute(color, 'blue')
        }
    }
    task producer(type: Producer) {
        outputFile = file("build/\${project.name}.jar")
    }
    artifacts {
        implementation producer.outputFile
    }
    task resolve {
        def view = configurations.implementation.incoming.artifactView {
            attributes.attribute(color, 'green')
        }.files
        dependsOn view
        doLast {
            println "result = \${view.files.name}"
        }
    }
}

class Producer extends DefaultTask {
    @OutputFile
    RegularFileProperty outputFile = project.objects.fileProperty()

    @TaskAction
    def go() {
        outputFile.get().asFile.text = "output"
    }
}

"""
    }

    /**
     * Each project produces 'blue' variants, and has a task that resolves the 'green' variant and a 'MakeGreen' transform that converts 'blue' to 'green'
     * Caller will need to provide an implementation of 'MakeGreen' transform
     */
    void setupBuildWithColorTransform() {
        setupBuildWithColorAttributes()
        buildFile << """
class EmptyParameters implements Serializable {}

allprojects {
    dependencies {
        registerTransform(EmptyParameters) {
            from.attribute(color, 'blue')
            to.attribute(color, 'green')
            actionClass = MakeGreen
        }
    }
}
"""
    }
}
