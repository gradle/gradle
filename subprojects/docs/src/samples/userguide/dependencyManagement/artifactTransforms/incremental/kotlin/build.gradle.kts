/*
 * Copyright 2019 the original author or authors.
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

import javax.inject.Inject

import org.gradle.api.artifacts.transform.TransformParameters

// tag::artifact-transform-countloc[]
abstract class CountLoc : TransformAction<TransformParameters.None> {

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputArtifact
    abstract val input: Provider<FileSystemLocation>

    override
    fun transform(outputs: TransformOutputs) {                          // <1>
        val outputDir = outputs.dir("${input.get().asFile.name}.loc")
        println("Running transform on ${input.get().asFile.name}, incremental: ${inputChanges.isIncremental}")
        inputChanges.getFileChanges(input).forEach { change ->          // <2>
            val changedFile = change.file
            if (change.fileType != FileType.FILE) {
                return@forEach
            }
            val outputLocation = outputDir.resolve("${change.normalizedPath}.loc")
            when (change.changeType) {
                ChangeType.ADDED, ChangeType.MODIFIED -> {

                    println("Processing file ${changedFile.name}")
                    outputLocation.parentFile.mkdirs()

                    outputLocation.writeText(changedFile.readLines().size.toString())
                }
                ChangeType.REMOVED -> {
                    println("Removing leftover output file ${outputLocation.name}")
                    outputLocation.delete()
                }
            }
        }
    }
}
// end::artifact-transform-countloc[]

val usage = Attribute.of("usage", String::class.java)
// tag::artifact-transform-registration[]
val artifactType = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(CountLoc::class) {
        from.attribute(artifactType, "java")
        to.attribute(artifactType, "loc")
    }
}
// end::artifact-transform-registration[]


allprojects {
    dependencies {
        attributesSchema {
            attribute(usage)
        }
    }
    configurations.create("compile") {
        attributes.attribute(usage, "api")
    }
}
