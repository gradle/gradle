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
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {

}

// Define a task that produces a custom artifact
tasks.register("createProductionArtifact", Jar::class) {
    archiveBaseName.set("production")
    from(sourceSets["main"].output)
    destinationDirectory.set(file("build/libs"))
}

configurations {
    // Define a custom configuration and extend from runtimeClasspath
    create("apiProductionElements") {
        extendsFrom(configurations.apiElements.get())
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("production"))
        }
        artifacts {
            add("apiProductionElements", tasks.named("createProductionArtifact"))
        }
    }
}

tasks.register("checkProducerVariants") {
    doLast {
        val producerProject = project(":producer")

        // Check the outgoing variants for the producer
        producerProject.configurations.forEach { config ->
            println("Configuration: ${config.name}")
            config.outgoing.artifacts.forEach {
                println("  - Artifact: ${it.file}")
            }
        }
    }
}

tasks.register("checkProducerAttributes") {
    doLast {
        configurations.forEach { config ->
            println("")
            println("Configuration: ${config.name}")
            println("Attributes:")
            config.attributes.keySet().forEach { key ->
                println("  - ${key.name} -> ${config.attributes.getAttribute(key)}")
            }
            println("Artifacts:")
            config.artifacts.forEach {
                println("${it.file}")
            }
        }
    }
}
