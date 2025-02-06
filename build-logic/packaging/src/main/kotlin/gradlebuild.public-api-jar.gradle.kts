/*
 * Copyright 2024 the original author or authors.
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

import gradlebuild.basics.ClassFileContentsAttribute
import gradlebuild.configureAsRuntimeElements
import gradlebuild.configureAsRuntimeJarClasspath

plugins {
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.module-identity")
    id("maven-publish")
    id("signing")
}

description = "Generates a public API jar and corresponding component to publish it"

// External API configurations remain unchanged
val externalApi = configurations.dependencyScope("externalApi") {
    description = "External dependencies that the public Gradle API depends on"
}

val externalRuntimeOnly = configurations.dependencyScope("externalRuntimeOnly") {
    dependencies.add(project.dependencies.create(project.dependencies.platform(project(":distributions-dependencies"))))
}

val externalRuntimeClasspath = configurations.resolvable("externalRuntimeClasspath") {
    extendsFrom(externalApi.get())
    extendsFrom(externalRuntimeOnly.get())
    configureAsRuntimeJarClasspath(objects)
}

// Distribution configurations
val distribution = configurations.dependencyScope("distribution") {
    description = "Dependencies to extract the public Gradle API from"
}

val distributionClasspath = configurations.resolvable("distributionClasspath") {
    extendsFrom(distribution.get())
    attributes {
        attribute(ImplementationCompletenessAttribute.attribute, ImplementationCompletenessAttribute.STUBS)
    }
}

// Enhanced filtering for API classes
val publicApiPatterns = setOf(
    // Core API packages
    "org/gradle/api/**",
    "org/gradle/external/javadoc/**",
    "org/gradle/process/**",
    // Include package-info files but handle duplicates
    "**/package-info.class",
    // Exclude internal implementations
    "!org/gradle/api/internal/**",
    "!org/gradle/internal/**"
)

val task = tasks.register<Jar>("jarGradleApi") {
    from(distributionClasspath.map { configuration ->
        configuration.incoming.artifactView {
            componentFilter { componentId -> 
                componentId is ProjectComponentIdentifier 
            }
        }.files
    }) {
        include(publicApiPatterns)
        
        // Handle package-info duplicates by keeping only the first occurrence
        eachFile {
            if (name == "package-info.class") {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
        
        // Exclude any test classes that might have slipped through
        exclude("**/*Test.class", "**/*Spec.class")
        
        // Preserve directory structure
        includeEmptyDirs = false
    }
    
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
    
    // Add manifest entries for API identification
    manifest {
        attributes(
            "Implementation-Title" to "Gradle API",
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to "org.gradle.api"
        )
    }
}

// Configuration and component setup remains unchanged
val gradleApiElements = configurations.consumable("gradleApiElements") {
    extendsFrom(externalApi.get())
    outgoing.artifact(task)
    configureAsRuntimeElements(objects)
}

open class SoftwareComponentFactoryProvider @Inject constructor(val factory: SoftwareComponentFactory)
val softwareComponentFactory = project.objects.newInstance(SoftwareComponentFactoryProvider::class.java).factory
val gradleApiComponent = softwareComponentFactory.adhoc("gradleApi")
components.add(gradleApiComponent)

gradleApiComponent.addVariantsFromConfiguration(gradleApiElements.get()) {
    mapToMavenScope("compile")
}
