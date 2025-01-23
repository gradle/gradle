/*
 * Copyright 2025 the original author or authors.
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

plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::artifact-views-with-custom-attribute[]
// The TestTransform class implements TransformAction,
// transforming input JAR files into text files with specific content
abstract class TestTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputFile = outputs.file("transformed-stub.txt")
        outputFile.writeText("Transformed from ${inputArtifact.get().asFile.name}")
    }
}

// The transform is registered to convert artifacts from the type "jar" to "stub"
dependencies {
    registerTransform(TestTransform::class.java) {
        from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
    }
}

dependencies {
    runtimeOnly("com.github.javafaker:javafaker:1.0.2")
}

// The testArtifact task queries and prints the attributes of resolved artifacts,
// showing the type conversion in action.
tasks.register("testArtifact") {
    val resolvedArtifacts = configurations.runtimeClasspath.get().incoming.artifactView {
        attributes {
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "stub")
        }
    }.artifacts.resolvedArtifacts

    doLast {
        resolvedArtifacts.get().forEach  {
            println("Resolved artifact variant:")
            println("- ${it.variant}")
            println("Resolved artifact attributes:")
            println("- ${it.variant.attributes}")
            println("Resolved artifact type:")
            println("- ${it.variant.attributes.getAttribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE)}")
        }
    }
}
// end::artifact-views-with-custom-attribute[]
