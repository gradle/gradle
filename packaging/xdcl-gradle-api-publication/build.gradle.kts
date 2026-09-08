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

import org.gradle.api.attributes.java.TargetJvmVersion
import java.util.jar.Attributes

/*
 * Republishes the XDCL Gradle API — the org.xdcl included build's `:xdcl-gradle-api` module
 * (package org.gradle.api.xdcl: the reaction SPI, the facade base types, ConfigurationNode) — as
 * `org.xdcl:xdcl-gradle-api` at the GRADLE DISTRIBUTION's version, from the same promotion build as
 * the `org.gradle:gradle-xdcl-*` ecosystem libraries.
 *
 * Why this module exists. The published ecosystem libraries expose the API types in their own API
 * (the generated facades extend them), so their published metadata must name the API module — at a
 * version some repository actually serves. That version is the distribution's: the libraries are
 * versioned with the distribution, and only gradle/gradle knows that version (version.txt plus the
 * build receipt or timestamp, computed in build logic here; there is no supported channel for a root
 * build to hand a computed value to an included build). Publishing records a project dependency
 * under the target project's PUBLICATION coordinates, so the ecosystem libraries depend on THIS
 * project (`gradlebuild.xdcl-ecosystem-library`, `compileOnlyApi`), and this project's publication
 * carries the org.xdcl coordinates with the distribution version — which is exactly what the
 * promotion build then publishes, and what the distribution's embedded Maven repository (`repo/`)
 * serves through this module's `gradlebuild.distribution-repository` slice.
 *
 * The jar is the included build's jar, repackaged (same classes, byte for byte; likewise the sources
 * and javadoc jars, reselected from the org.xdcl module's documentation variants). This project has
 * no sources of its own. It is NOT bundled in the distribution image: the runtime copy of these
 * classes there is the included build's own jar under lib/, reached through the provider chain, and
 * the ecosystem libraries keep this project off their runtime variant (`compileOnlyApi`) so the image
 * never carries the classes twice.
 */

plugins {
    `java-library`
    id("gradlebuild.distribution-repository")
    id("gradlebuild.reproducible-archives")
}

description = "The XDCL Gradle API (org.gradle.api.xdcl): the reaction SPI, the facade base types the generated ecosystem facades extend, and the ConfigurationNode accessor. Republished from the org.xdcl build at the Gradle distribution's version (prototype)"

// The API's bytecode level (the org.xdcl build compiles it with a Java 17 toolchain). Nothing is
// compiled here, but these drive the `org.gradle.jvm.version` attribute of the published variants.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

// The included build's artifacts: the jar through an ordinary library resolution (the composite
// substitutes the catalog coordinates with the `:xdcl-gradle-api` project), the sources and javadoc
// jars by variant reselection off the same resolution.
val xdclGradleApi = configurations.dependencyScope("xdclGradleApi") {
    description = "The org.xdcl API module republished by this project"
}
val xdclGradleApiClasspath = configurations.resolvable("xdclGradleApiClasspath") {
    description = "Resolves the org.xdcl API jar republished by this project"
    extendsFrom(xdclGradleApi.get())
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}

dependencies {
    xdclGradleApi(libs.xdclGradleApi)
}

fun documentationOf(docsType: String): Provider<File> =
    xdclGradleApiClasspath.get().incoming.artifactView {
        withVariantReselection()
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(docsType))
        }
    }.files.elements.map { it.single().asFile }

val apiJar: Provider<File> = xdclGradleApiClasspath.get().incoming.files.elements.map { it.single().asFile }
val apiSourcesJar: Provider<File> = documentationOf(DocsType.SOURCES)
val apiJavadocJar: Provider<File> = documentationOf(DocsType.JAVADOC)

// Repackage rather than re-export the file: the java component's artifacts must be produced by this
// project's own archive tasks for the publication (and its module metadata) to describe them. The
// included jars' manifests are dropped in favour of this project's — gradlebuild.module-identity's,
// with the title naming the API rather than "Gradle" (the version stays the distribution's).
tasks.withType<Jar>().configureEach {
    manifest.attributes(mapOf(Attributes.Name.IMPLEMENTATION_TITLE.toString() to "XDCL Gradle API"))
}
tasks.named<Jar>("jar") {
    from(zipTree(apiJar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}
tasks.named<Jar>("sourcesJar") {
    from(zipTree(apiSourcesJar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}
tasks.named<Jar>("javadocJar") {
    from(zipTree(apiJavadocJar)) {
        exclude("META-INF/MANIFEST.MF")
    }
}

// The publication's coordinates are the org.xdcl module's, at this project's (= the distribution's)
// version — everything else (remote repository, signing, POM boilerplate, the `promotionBuild`
// lifecycle hook) comes from gradlebuild.publish-public-libraries via the distribution-repository
// convention. The sources are the org.xdcl repository's, so the SCM points there.
publishing {
    publications {
        named<MavenPublication>("gradleDistribution") {
            groupId = "org.xdcl"
            artifactId = "xdcl-gradle-api"
            pom {
                name = "org.xdcl:xdcl-gradle-api"
                scm {
                    connection = "scm:git:git://github.com/gradle/xdcl.git"
                    developerConnection = "scm:git:ssh://github.com:gradle/xdcl.git"
                    url = "https://github.com/gradle/xdcl"
                }
            }
        }
    }
}
