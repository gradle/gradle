/*
 * Copyright 2026 the original author or authors.
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

import gradlebuild.basics.PublicApiVariants
import gradlebuild.configureAsApiElements
import gradlebuild.configureAsRuntimeJarClasspath
import gradlebuild.packaging.support.ArtifactViewHelper.lenientProjectArtifactReselection
import gradlebuild.packaging.support.FileLinesValueSource
import gradlebuild.packaging.support.includePublicApiAbiStubs
import gradlebuild.packaging.support.publicApiAbiStubs
import gradlebuild.packaging.tasks.PathPrefixLister
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.kotlin.dsl.support.serviceOf

// Publishes the public API produced by `gradlebuild.public-api-jar` as Maven components, and exposes
// it for consumption. Only `:public-api` applies this; distributions produce the jar but never publish it.
plugins {
    id("gradlebuild.public-api-jar")
    id("maven-publish")
    id("signing")
}

description = "Publishes the public API and corresponding components"

// Defines configurations used to resolve external dependencies that the published API variants declare.
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

// The legacy variant inherits everything in the public API, minus the relocated impldeps qdox and javaparser-core.
val legacyExternalApi = configurations.dependencyScope("legacyExternalApi") {
    description = "External dependencies that the legacy public Gradle API depends on"
    extendsFrom(externalApi.get())
    exclude(module = "qdox")
    exclude(module = "javaparser-core")
}
val legacyExternalRuntimeOnly = configurations.dependencyScope("legacyExternalRuntimeOnly") {
    dependencies.add(project.dependencies.create(project.dependencies.platform(project(":distributions-dependencies"))))
}
val legacyExternalRuntimeClasspath = configurations.resolvable("legacyExternalRuntimeClasspath") {
    extendsFrom(legacyExternalApi.get())
    extendsFrom(legacyExternalRuntimeOnly.get())
    configureAsRuntimeJarClasspath(objects)
}

val internalBaseName = gradleModule.identity.baseName.map { "$it${PublicApiVariants.INTERNAL_SUFFIX}" }
val legacyBaseName = gradleModule.identity.baseName.map { "$it${PublicApiVariants.LEGACY_SUFFIX}" }

// Stub source produced by gradlebuild.public-api-jar; reused for the internal jar and the sources jar.
val distributionClasspath = configurations.named<ResolvableConfiguration>("distributionClasspath")

val apiJarTask = tasks.register<Jar>("jarGradleApi") {
    includePublicApiAbiStubs(publicApiAbiStubs(distributionClasspath))
    archiveBaseName = internalBaseName
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
}

// The consumable configuration containing the public Gradle API artifact and its external dependencies.
val gradleApiElements = configurations.consumable("gradleApiElements") {
    extendsFrom(externalApi.get())
    outgoing.artifact(apiJarTask)
    outgoing.capability(internalBaseName.map { "$group:$it:$version" })
    configureAsApiElements(objects)
}

val legacyGradleApiElements = configurations.consumable("legacyGradleApiElements") {
    extendsFrom(legacyExternalApi.get())
    outgoing.artifact(tasks.named("jarGradleApiLegacy"))
    outgoing.capability(legacyBaseName.map { "$group:$it:$version" })
    configureAsApiElements(objects)
}

val pathPrefixListTask = tasks.register<PathPrefixLister>("generateGradleApiPathPrefixList") {
    apiJar.set(apiJarTask.flatMap { it.archiveFile })
    pathPrefixFile.set(layout.buildDirectory.file("public-api/path-prefix-list.txt"))
}

val sourceJarTask = tasks.register<Jar>("sourcesJar") {
    val pathPrefixes = providers.of(FileLinesValueSource::class.java) {
        parameters.inputFile.set(pathPrefixListTask.flatMap { it.pathPrefixFile })
    }

    val archiveOperations = project.serviceOf<ArchiveOperations>()
    // We use the resolvable configuration, but leverage withVariantReselection to obtain the subset of sources
    from(lenientProjectArtifactReselection(
        distributionClasspath,
        {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named("documentation"))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("sources"))
            },
        { fileCollection -> fileCollection.elements.map { set -> set.map { file -> archiveOperations.zipTree(file.asFile) } }}
    )) {
        include { element ->
            val parent = element.relativePath.parent
            if (parent == null) false
            else pathPrefixes.get().contains(parent.pathString)
        }
    }

    inputs.files(pathPrefixListTask.flatMap { it.pathPrefixFile }).withPropertyName("pathPrefixList").withPathSensitivity(PathSensitivity.NONE)
    archiveClassifier.set("sources")
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
    // This is needed because of the duplicate package-info.class files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// TODO: SoftwareComponentFactoryProvider can be replaced with PublishingExtension#getSoftwareComponentFactory()
open class SoftwareComponentFactoryProvider @Inject constructor(val factory: SoftwareComponentFactory)

val softwareComponentFactory = project.objects.newInstance(SoftwareComponentFactoryProvider::class.java).factory
val gradleApiComponent = softwareComponentFactory.adhoc("gradleApi")
components.add(gradleApiComponent)

// Published component containing the public Gradle API
gradleApiComponent.addVariantsFromConfiguration(gradleApiElements) {
    mapToMavenScope("compile")
}

val sourcesElements = configurations.consumable("sourcesElements") {
    outgoing.artifact(sourceJarTask)
    outgoing.capability(internalBaseName.map { "$group:$it:$version" })
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType.SOURCES))
        attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, named(Usage.JAVA_RUNTIME))
    }
}

gradleApiComponent.addVariantsFromConfiguration(sourcesElements) {
    mapToOptional()
}

// Published component containing the legacy public Gradle API
val legacyGradleApiComponent = softwareComponentFactory.adhoc("legacyGradleApi")
components.add(legacyGradleApiComponent)
legacyGradleApiComponent.addVariantsFromConfiguration(legacyGradleApiElements) {
    mapToMavenScope("compile")
}
