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

import gradlebuild.basics.ImplementationCompletenessAttribute
import gradlebuild.configureAsCompileJarClasspath
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

// Defines configurations used to resolve external dependencies
// that the public API depends on.
// TODO: We should be able to derive these dependencies automatically.
//       In fact, our public API should have no external dependencies.
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

// Defines configurations used to resolve the public Gradle API.
val distribution = configurations.dependencyScope("distribution") {
    description = "Dependencies to extract the public Gradle API from"
}
val distributionClasspath = configurations.resolvable("distributionClasspath") {
    extendsFrom(distribution.get())
    configureAsCompileJarClasspath(objects)
    attributes {
        attribute(ImplementationCompletenessAttribute.attribute, ImplementationCompletenessAttribute.STUBS)
    }
}

val task = tasks.register<Jar>("jarGradleApi") {
    from(distributionClasspath.map { configuration ->
        configuration.incoming.artifactView {
            componentFilter { componentId -> componentId is ProjectComponentIdentifier }
        }.files
    })
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
    // This is needed because of the duplicate package-info.class files
    duplicatesStrategy = DuplicatesStrategy.WARN
}

// The consumable configuration containing the public Gradle API artifact
// and its external dependencies.
val gradleApiElements = configurations.consumable("gradleApiElements") {
    extendsFrom(externalApi.get())
    outgoing.artifact(task)
    configureAsRuntimeElements(objects)
}

open class SoftwareComponentFactoryProvider @Inject constructor(val factory: SoftwareComponentFactory)
val softwareComponentFactory = project.objects.newInstance(SoftwareComponentFactoryProvider::class.java).factory
val gradleApiComponent = softwareComponentFactory.adhoc("gradleApi")
components.add(gradleApiComponent)

// Published component containing the public Gradle API
gradleApiComponent.addVariantsFromConfiguration(gradleApiElements.get()) {
    mapToMavenScope("compile")
}
