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
    id("application")
}

repositories {
    mavenCentral()
}

// Declare the dependency on the producer project
dependencies {
    implementation(project(":producer"))
}

tasks.register("checkResolvedVariant") {
    doLast {
        project.configurations.forEach { configuration ->
            // Skip `test*` configurations and `annotationProcessor`
            if (configuration.name.startsWith("test") || configuration.name == "annotationProcessor") {
                return@forEach
            }
            // Otherwise print info
            if (configuration.isCanBeResolved) {
                println("Configuration: ${configuration.name}")
                val resolvedArtifacts = configuration.incoming.artifacts.resolvedArtifacts
                resolvedArtifacts.get().forEach { artifact ->
                    println("-Artifact: ${artifact.file}")
                }
                val resolvedComponents = configuration.incoming.resolutionResult.allComponents
                resolvedComponents.forEach { component ->
                    if (component.id.displayName == "project :producer") {
                        println("- Component: ${component.id}")
                        component.variants.forEach {
                            println("    - Variant: ${it}")
                            it.attributes.keySet().forEach { key ->
                                println("       - ${key.name} -> ${it.attributes.getAttribute(key)}")
                            }
                        }
                    }
                }
            }
        }

    }
}

tasks.register("artifactWithAttributeAndView") {
    doLast {
        val configuration = configurations.runtimeClasspath.get()
        println("Attributes used to resolve '${configuration.name}':")
        configuration.attributes.keySet().forEach { attribute ->
            val value = configuration.attributes.getAttribute(attribute)
            println("  - ${attribute.name} = $value")
        }

        println("\nAttributes in ArtifactView for 'LibraryElements = classes:'")
        val artifactView = configuration.incoming.artifactView {
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("classes"))
            }
        }

        artifactView.artifacts.artifactFiles.files.forEach {
            println("- Artifact: ${it.name}")
        }

        artifactView.attributes.keySet().forEach { attribute ->
            val value = artifactView.attributes.getAttribute(attribute)
            println("  - ${attribute.name} = $value")
        }
    }
}

tasks.register("artifactWithAttributeAndVariantReselectionView") {
    doLast {
        val configuration = configurations.runtimeClasspath.get()
        println("Attributes used to resolve '${configuration.name}':")
        configuration.attributes.keySet().forEach { attribute ->
            val value = configuration.attributes.getAttribute(attribute)
            println("  - ${attribute.name} = $value")
        }

        println("\nAttributes in ArtifactView for 'Category = production:'")
        val artifactView = configuration.incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named("production"))
            }
        }

        artifactView.artifacts.artifactFiles.files.forEach {
            println("- Artifact: ${it.name}")
        }

        artifactView.attributes.keySet().forEach { attribute ->
            val value = artifactView.attributes.getAttribute(attribute)
            println("  - ${attribute.name} = $value")
        }
    }
}
