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

import gradlebuild.configureAsApiElements
import gradlebuild.configureAsRuntimeJarClasspath
import gradlebuild.packaging.support.ArtifactViewHelper.lenientProjectArtifactReselection
import gradlebuild.packaging.support.FileLinesValueSource
import gradlebuild.packaging.tasks.PathPrefixLister
import org.gradle.kotlin.dsl.support.serviceOf

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

// Resolvable configuration to get all projects having a runtime JAR
val distributionClasspath = configurations.resolvable("distributionClasspath") {
    extendsFrom(distribution.get())
    configureAsRuntimeJarClasspath(objects)
}

val apiJarTask = tasks.register<Jar>("jarGradleApi") {
    // We use the resolvable configuration, but leverage withVariantReselection to obtain the subset of api stubs artifacts
    // Some projects simply don't have one, which excludes them
    from(lenientProjectArtifactReselection<FileCollection>(
        distributionClasspath,
        {
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named("api-stubs"))
            }
    )) {
        // TODO Use better filtering
        include("**/*.class")
        include("META-INF/*.kotlin_module")
    }
    destinationDirectory = layout.buildDirectory.dir("public-api/gradle-api")
    // This is needed because of the duplicate package-info.class files
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// The consumable configuration containing the public Gradle API artifact
// and its external dependencies.
val gradleApiElements = configurations.consumable("gradleApiElements") {
    extendsFrom(externalApi.get())
    outgoing.artifact(apiJarTask)
    configureAsApiElements(objects)
}

val pathPrefixListTask = tasks.register<PathPrefixLister>("generateGradleApiPathPrefixList") {
    apiJar.set(apiJarTask.flatMap { it.archiveFile} )
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
