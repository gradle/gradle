/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.packaging

import accessors.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import org.gradle.api.tasks.bundling.Jar

/**
 * Creates a shaded jar of the publication of the current project.
 *
 * The shaded jar is added as an artifact to the {@code publishRuntime} configuration.
 */
open class ToolingApiShadedJarPlugin : Plugin<Project> {
    val artifactType: Attribute<String> = Attribute.of("artifactType", String::class.java)
    val minified: Attribute<Boolean> = Attribute.of("minified", Boolean::class.javaObjectType)

    override fun apply(project: Project): Unit = project.run {

        val jarsToShade by configurations.creating
        jarsToShade.apply {
            exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            isCanBeResolved = true
            isCanBeConsumed = false
        }

        val shadedJar = extensions.create<ShadedJarExtension>("shadedJar", layout, objects, jarsToShade)

        dependencies {
            registerTransform {
                from.attribute(artifactType, "relocatedClassesAndAnalysis")
                to.attribute(artifactType, "relocatedClasses")
                artifactTransform(FindRelocatedClasses::class.java)
            }
            registerTransform {
                from.attribute(artifactType, "relocatedClassesAndAnalysis")
                to.attribute(artifactType, "entryPoints")
                artifactTransform(FindEntryPoints::class.java)
            }
            registerTransform {
                from.attribute(artifactType, "relocatedClassesAndAnalysis")
                to.attribute(artifactType, "classTrees")
                artifactTransform(FindClassTrees::class.java)
            }
            registerTransform {
                from.attribute(artifactType, "relocatedClassesAndAnalysis")
                to.attribute(artifactType, "manifests")
                artifactTransform(FindManifests::class.java)
            }
        }

        val baseVersion: String by rootProject.extra
        val jar: Jar by tasks

        val toolingApiShadedJar by tasks.creating(ToolingApiShadedJar::class.java) {
            dependsOn(jar)
            jarFile.set(layout.buildDirectory.file("shaded-jar/gradle-tooling-api-shaded-$baseVersion.jar"))
            classTreesConfiguration = jarsToShade.artifactViewForType("classTrees")
            entryPointsConfiguration = jarsToShade.artifactViewForType("entryPoints")
            relocatedClassesConfiguration = jarsToShade.artifactViewForType("relocatedClasses")
            manifests = jarsToShade.artifactViewForType("manifests")
            buildReceiptFile.set(shadedJar.buildReceiptFile)
        }

        artifacts.add("publishRuntime", mapOf(
            "file" to toolingApiShadedJar.jarFile.get().asFile,
            "name" to base.archivesBaseName,
            "type" to "jar",
            "builtBy" to toolingApiShadedJar
        ))

        afterEvaluate {
            dependencies {
                registerTransform {
                    from.attribute(artifactType, "jar").attribute(minified, true)
                    to.attribute(artifactType, "relocatedClassesAndAnalysis")
                    artifactTransform(ShadeClassesTransform::class.java) {
                        params(
                            "org.gradle.internal.impldep",
                            shadedJar.keepPackages.get(),
                            shadedJar.unshadedPackages.get(),
                            shadedJar.ignoredPackages.get()
                        )
                    }
                }

                add(jarsToShade.name, project)
            }
        }
    }

    fun Configuration.artifactViewForType(artifactTypeName: String) = incoming.artifactView {
        attributes.attribute(artifactType, artifactTypeName)
    }.files

}

open class ShadedJarExtension(layout: ProjectLayout, objects: ObjectFactory, val shadedConfiguration: Configuration) {
    val buildReceiptFile = layout.fileProperty()
    val keepPackages = objects.setProperty(String::class.java)!!
    val unshadedPackages = objects.setProperty(String::class.java)!!
    val ignoredPackages = objects.setProperty(String::class.java)!!
}
