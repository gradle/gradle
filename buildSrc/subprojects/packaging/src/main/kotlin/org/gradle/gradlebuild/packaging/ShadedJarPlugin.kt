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

import accessors.base
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.gradlebuild.packaging.Attributes.artifactType
import org.gradle.gradlebuild.packaging.Attributes.minified
import org.gradle.kotlin.dsl.*
import java.io.File


private
const val relocatedClassesAndAnalysisType = "relocatedClassesAndAnalysis"


private
const val relocatedClassesType = "relocatedClasses"


private
const val entryPointsType = "entryPoints"


private
const val classTreesType = "classTrees"


private
const val manifestsType = "manifests"


/**
 * Creates a shaded jar of the publication of the current project.
 *
 * The shaded jar is added as an artifact to the {@code publishRuntime} configuration.
 */
open class ShadedJarPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        val shadedJarExtension = createShadedJarExtension(createConfigurationToShade())

        registerTransforms(shadedJarExtension)

        val shadedJarTask = addShadedJarTask(shadedJarExtension)

        addInstallShadedJarTask(shadedJarTask)

        plugins.withId("gradlebuild.publish-public-libraries") {
            addShadedJarArtifact(shadedJarTask)
        }
    }

    private
    fun Project.createShadedJarExtension(configurationToShade: Configuration) =
        extensions.create<ShadedJarExtension>("shadedJar", objects, configurationToShade)

    private
    fun Project.registerTransforms(shadedJarExtension: ShadedJarExtension) {
        afterEvaluate {
            dependencies {
                registerTransform(ShadeClasses::class) {
                    from
                        .attribute(artifactType, "jar")
                        .attribute(minified, true)
                    to.attribute(artifactType, relocatedClassesAndAnalysisType)
                    parameters {
                        shadowPackage = "org.gradle.internal.impldep"
                        keepPackages = shadedJarExtension.keepPackages.get()
                        unshadedPackages = shadedJarExtension.unshadedPackages.get()
                        ignoredPackages = shadedJarExtension.ignoredPackages.get()
                    }
                }
            }
        }
        dependencies {
            registerTransform(FindRelocatedClasses::class) {
                from.attribute(artifactType, relocatedClassesAndAnalysisType)
                to.attribute(artifactType, relocatedClassesType)
            }
            registerTransform(FindEntryPoints::class) {
                from.attribute(artifactType, relocatedClassesAndAnalysisType)
                to.attribute(artifactType, entryPointsType)
            }
            registerTransform(FindClassTrees::class) {
                from.attribute(artifactType, relocatedClassesAndAnalysisType)
                to.attribute(artifactType, classTreesType)
            }
            registerTransform(FindManifests::class) {
                from.attribute(artifactType, relocatedClassesAndAnalysisType)
                to.attribute(artifactType, manifestsType)
            }
        }
    }

    private
    fun Project.createConfigurationToShade(): Configuration {
        val configurationName = "jarsToShade"
        afterEvaluate {
            dependencies.add(configurationName, project)
        }

        return configurations.create(configurationName) {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            isCanBeResolved = true
            isCanBeConsumed = false
        }
    }

    private
    fun Project.addShadedJarTask(shadedJarExtension: ShadedJarExtension): TaskProvider<ShadedJar> {
        val configurationToShade = shadedJarExtension.shadedConfiguration
        val jar: TaskProvider<Jar> = tasks.withType(Jar::class).named("jar")

        return tasks.register("${project.name}ShadedJar", ShadedJar::class) {
            dependsOn(jar)
            jarFile.set(layout.buildDirectory.file("shaded-jar/${base.archivesBaseName}-shaded-${rootProject.extra["baseVersion"]}.jar"))
            classTreesConfiguration.from(configurationToShade.artifactViewForType(classTreesType))
            entryPointsConfiguration.from(configurationToShade.artifactViewForType(entryPointsType))
            relocatedClassesConfiguration.from(configurationToShade.artifactViewForType(relocatedClassesType))
            manifests.from(configurationToShade.artifactViewForType(manifestsType))
            buildReceiptFile.set(shadedJarExtension.buildReceiptFile)
        }
    }

    private
    fun Project.addInstallShadedJarTask(shadedJarTask: TaskProvider<ShadedJar>) {
        val installPathProperty = "${project.name}ShadedJarInstallPath"
        fun targetFile(): File {
            val file = findProperty(installPathProperty)?.let { File(findProperty(installPathProperty) as String) }

            if (true == file?.isAbsolute) {
                return file
            } else {
                throw IllegalArgumentException("Property $installPathProperty is required and must be absolute!")
            }
        }
        tasks.register<Copy>("install${project.name.capitalize()}ShadedJar") {
            dependsOn(shadedJarTask)
            from(shadedJarTask.map { it.jarFile })
            into(deferred { targetFile().parentFile })
            rename { targetFile().name }
        }
    }

    private
    fun Project.addShadedJarArtifact(shadedJarTask: TaskProvider<ShadedJar>) {
        artifacts.add("publishRuntime", mapOf(
            "file" to shadedJarTask.get().jarFile.get().asFile,
            "name" to base.archivesBaseName,
            "type" to "jar",
            "builtBy" to shadedJarTask
        ))
    }

    private
    fun Configuration.artifactViewForType(artifactTypeName: String) = incoming.artifactView {
        attributes.attribute(artifactType, artifactTypeName)
    }.files
}


open class ShadedJarExtension(objects: ObjectFactory, val shadedConfiguration: Configuration) {

    /**
     * The build receipt properties file.
     *
     * The file will be included in the shaded jar under {@code /org/gradle/build-receipt.properties}.
     */
    val buildReceiptFile = objects.fileProperty()

    /**
     * Retain only those classes in the keep package hierarchies, plus any classes that are reachable from these classes.
     */
    val keepPackages = objects.setProperty(String::class)

    /**
     * Do not rename classes in the unshaded package hierarchies. Always includes 'java'.
     */
    val unshadedPackages = objects.setProperty(String::class)

    /**
     * Do not retain classes in the ignore packages hierarchies, unless reachable from some other retained class.
     */
    val ignoredPackages = objects.setProperty(String::class)
}
