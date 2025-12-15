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

import gradlebuild.basics.tasks.ClasspathManifest
import gradlebuild.configureAsApiElements
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

// Defines configurations used to resolve external dependencies
// that the legacy public API depends on.
val legacyExternalApi = configurations.dependencyScope("legacyExternalApi") {
    description = "External dependencies that the legacy public Gradle API depends on"
}
val legacyExternalRuntimeOnly = configurations.dependencyScope("legacyExternalRuntimeOnly") {
    dependencies.add(project.dependencies.create(project.dependencies.platform(project(":distributions-dependencies"))))
}
val legacyExternalRuntimeClasspath = configurations.resolvable("legacyExternalRuntimeClasspath") {
    extendsFrom(legacyExternalApi.get())
    extendsFrom(legacyExternalRuntimeOnly.get())
    configureAsRuntimeJarClasspath(objects)
}

// Defines configurations used to resolve the public Gradle API.
val distribution = configurations.dependencyScope("distribution") {
    description = "Dependencies to extract the public Gradle API from"
}

// Resolvable configuration to get all projects having a runtime JAR
val distributionClasspath = configurations.resolvable("distributionClasspath") {
    extendsFrom(distribution.get())
    configureAsRuntimeJarClasspath(objects)
}

val classpathManifest = tasks.register<ClasspathManifest>("classpathManifest") {
    manifestFile = layout.buildDirectory.dir("generated-resources/classpath-manifest")
        .zip(gradleModule.identity.baseName) { dir, baseName ->
            dir.file("$baseName-classpath.properties")
        }
    externalDependencies.from(externalRuntimeClasspath)
}

val legacyClasspathManifest = tasks.register<ClasspathManifest>("legacyClasspathManifest") {
    manifestFile = layout.buildDirectory.dir("generated-resources/legacy-classpath-manifest")
        .zip(gradleModule.identity.baseName) { dir, baseName ->
            dir.file("$baseName-classpath.properties")
        }
    externalDependencies.from(legacyExternalRuntimeClasspath)
}

fun Jar.commonPublicApiJarConfiguration() {
    // We use the resolvable configuration, but leverage withVariantReselection to obtain the subset of api stubs artifacts
    // Some projects simply don't have one, which excludes them
    from(distributionClasspath.map { configuration ->
        configuration.incoming.artifactView {
            withVariantReselection()
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named("api-stubs"))
            }
            lenient(true)
            componentFilter { componentId -> componentId is ProjectComponentIdentifier }
        }.files
    }) {
        // TODO Use better filtering
        include("**/*.class")
        include("META-INF/*.kotlin_module")
    }
    // This is needed because of the duplicate package-info.class files, it is safe thanks to PackageInfoTest
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val task = tasks.register<Jar>("jarGradleApi") {
    commonPublicApiJarConfiguration()
    from(classpathManifest)
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
}
val legacyTask = tasks.register<Jar>("jarGradleApiLegacy") {
    commonPublicApiJarConfiguration()
    from(legacyClasspathManifest)
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api-legacy")
}

// The consumable configuration containing the public Gradle API artifact
// and its external dependencies.
val gradleApiElements = configurations.consumable("gradleApiElements") {
    extendsFrom(externalApi.get())
    outgoing.artifact(task)
    configureAsApiElements(objects)
}

val legacyGradleApiElements = configurations.consumable("legacyGradleApiElements") {
    extendsFrom(legacyExternalApi.get())
    outgoing.artifact(legacyTask)
    configureAsApiElements(objects)
}

// TODO: SoftwareComponentFactoryProvider can be replaced with PublishingExtension#getSoftwareComponentFactory()
open class SoftwareComponentFactoryProvider @Inject constructor(val factory: SoftwareComponentFactory)

val softwareComponentFactory = project.objects.newInstance(SoftwareComponentFactoryProvider::class.java).factory

// Published component containing the public Gradle API
val gradleApiComponent = softwareComponentFactory.adhoc("gradleApi")
components.add(gradleApiComponent)
gradleApiComponent.addVariantsFromConfiguration(gradleApiElements.get()) {
    mapToMavenScope("compile")
}

// Published component containing the legacypublic Gradle API
val legacyGradleApiComponent = softwareComponentFactory.adhoc("legacyGradleApi")
components.add(legacyGradleApiComponent)
legacyGradleApiComponent.addVariantsFromConfiguration(legacyGradleApiElements.get()) {
    mapToMavenScope("compile")
}
