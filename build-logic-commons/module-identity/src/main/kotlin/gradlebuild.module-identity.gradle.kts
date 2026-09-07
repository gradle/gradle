/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.basics.buildFinalRelease
import gradlebuild.basics.buildMilestoneNumber
import gradlebuild.basics.buildRcNumber
import gradlebuild.basics.buildTimestamp
import gradlebuild.basics.buildVersionQualifier
import gradlebuild.basics.isPromotionBuild
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.basics.repoRoot
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.extension.ReleasedVersionsDetails
import java.util.Optional
import java.util.jar.Attributes

plugins {
    `java-base`
    id("org.gradle.pom-properties")
}

val gradleModule = extensions.create<GradleModuleExtension>(GradleModuleExtension.NAME).apply {
    published = false

    requiredRuntimes {
        client = false
        daemon = false
        worker = false
    }

    computedRuntimes {
        client = false
        daemon = false
        worker = false
    }

    // TODO: Most of these properties are the same across projects. We should
    // compute these at the settings-level instead of the project-level.
    identity {
        baseName = "gradle-$name"
        group = "org.gradle"
        buildTimestamp = buildTimestamp()
        promotionBuild = isPromotionBuild

        val finalReleaseSuffix = buildFinalRelease.map { "" }
        val rcSuffix = buildRcNumber.map { "-rc-$it" }
        val milestoneSuffix = buildMilestoneNumber.map { "-milestone-$it" }
        val buildVersionQualifierSuffix = buildVersionQualifier.zip(buildTimestamp) { buildVersion, timestamp -> "-$buildVersion-$timestamp" }
        val buildTimestampSuffix = buildTimestamp.map { "-$it" }

        val specifiedSuffix = atMostOneOf(finalReleaseSuffix, rcSuffix, milestoneSuffix)
        val computedSuffix = specifiedSuffix
            .orElse(buildVersionQualifierSuffix)
            .orElse(buildTimestampSuffix)

        val baseVersion = trimmedContentsOfFile("version.txt")
        version = baseVersion.zip(computedSuffix) { base, suffix -> GradleVersion.version("$base$suffix") }
        snapshot = specifiedSuffix.map { false }.orElse(true)

        // Same suffix decision as `version`, except that the two timestamped forms record
        // SNAPSHOT instead of the timestamp. Derived here rather than by rewriting `version`
        // afterwards, so the version qualifier is kept and no timestamp is needed at all.
        val reproducibleSuffix = specifiedSuffix
            .orElse(buildVersionQualifier.map { "-$it-SNAPSHOT" })
            .orElse("-SNAPSHOT")
        reproducibleVersion = baseVersion.zip(reproducibleSuffix) { base, suffix -> "$base$suffix" }
        releasedVersions = version.map {
            ReleasedVersionsDetails(
                it.baseVersion,
                releasedVersionsFile()
            )
        }
    }
}

/**
 * Wraps a lazily computed value for assignment to `Project.group` / `Project.version`, which are
 * plain `Object` and always read through `toString()`. Without this the project properties would
 * capture their value at apply time and a module that overrides its identity afterwards - as
 * `:public-api` does - would not be reflected in them.
 */
class LazyProjectProperty(private val value: Provider<String>) {
    override fun toString(): String = value.get()
}

// ModuleIdentity is the source of truth; the project properties derive from it.
group = LazyProjectProperty(gradleModule.identity.group)
version = LazyProjectProperty(gradleModule.identity.version.map { it.version })

tasks.withType<Jar>().configureEach {
    archiveBaseName = gradleModule.identity.baseName
    archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
    manifest.attributes(
        mapOf(
            // Maven Archiver writes Implementation-Title from the POM name and
            // Implementation-Vendor from the organization; we follow it for the vendor.
            //
            // The title is "Gradle" rather than the module name because it serves as product
            // evidence for CPE-based scanners, matching `cpe:2.3:a:gradle:gradle` and the value
            // every earlier release already carries. Scanners that key off Maven coordinates do
            // not read it: pom.properties takes precedence over the manifest for them.
            Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
            Attributes.Name.IMPLEMENTATION_VERSION.toString() to gradleModule.identity.reproducibleVersion,
            Attributes.Name.IMPLEMENTATION_VENDOR.toString() to "Gradle, Inc.",
            // Not a Maven Archiver attribute. It is read first by scanners deriving a groupId
            // from the manifest (Trivy tries Implementation-Vendor-Id, then Bundle-SymbolicName,
            // then Implementation-Vendor), so without it they would take the vendor name above
            // as the group. Spelled out because the JDK constant is deprecated for removal.
            "Implementation-Vendor-Id" to gradleModule.identity.group
        )
    )
}

// The org.gradle.pom-properties plugin adds a Maven-style pom.properties to the standard `jar`,
// at the same path and with the same keys Maven Archiver writes. Take the coordinates from
// ModuleIdentity so they match the manifest and how the module is published.
pomProperties {
    groupId = gradleModule.identity.group
    artifactId = gradleModule.identity.baseName
    version = gradleModule.identity.reproducibleVersion
}

// The plugin only wires the standard `jar`. When the Shadow plugin is applied, the distribution
// ships the `shadowJar` output in its place, so it needs the same pom.properties.
pluginManager.withPlugin("com.gradleup.shadow") {
    tasks.named<Jar>("shadowJar") {
        from(tasks.named("generatePomProperties"))
    }
}

/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
fun Project.trimmedContentsOfFile(path: String): Provider<String> =
    providers.fileContents(repoRoot().file(path)).asText.map { it.trim() }


/**
 * Returns a new provider that takes its value from at most one
 * of the given providers. If no input provider is present, the output
 * provider will not be present. If more than one input provider
 * has a value specified, the resulting provider will throw an
 * exception when queried.
 */
fun <T: Any> atMostOneOf(vararg providers: Provider<T>): Provider<T> {
    return providers.map { provider ->
        provider.map {
            Optional.of(it)
        }.orElse(
            Optional.empty<T>()
        )
    }.reduce { acc, next ->
        acc.zip(next) { left, right ->
            when {
                left.isPresent -> {
                    require(!right.isPresent) {
                        "Expected at most one provider to be present"
                    }
                    left
                }
                else -> right
            }
        }
    }.map { it.orElse(null) }
}
