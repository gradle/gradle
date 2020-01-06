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

package org.gradle.gradlebuild.test.integrationtests

import accessors.base
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Sync
import org.gradle.gradlebuild.packaging.ShadedJar
import org.gradle.gradlebuild.testing.integrationtests.cleanup.CleanupExtension
import org.gradle.internal.classloader.ClasspathHasher
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.File
import kotlin.collections.set


class DistributionTestingPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks.withType<DistributionTest>().configureEach {
            dependsOn(":toolingApi:toolingApiShadedJar")
            dependsOn(":cleanUpCaches")
            finalizedBy(":cleanUpDaemons")
            shouldRunAfter("test")

            setJvmArgsOfTestJvm()
            setSystemPropertiesOfTestJVM(project)
            configureGradleTestEnvironment(rootProject.providers, rootProject.layout, rootProject.base, rootProject.objects)
            addSetUpAndTearDownActions(project)
        }
    }

    private
    fun DistributionTest.addSetUpAndTearDownActions(project: Project) {
        val cleanupExtension = project.rootProject.extensions.getByType(CleanupExtension::class.java)
        this.tracker.set(cleanupExtension.tracker)
    }

    private
    fun DistributionTest.configureGradleTestEnvironment(providers: ProviderFactory, layout: ProjectLayout, basePluginConvention: BasePluginConvention, objects: ObjectFactory) {

        val projectDirectory = layout.projectDirectory

        // TODO: Replace this with something in the Gradle API to make this transition easier
        fun dirWorkaround(directory: () -> File): Provider<Directory> = objects.directoryProperty().also {
            it.set(projectDirectory.dir(providers.provider { directory().absolutePath }))
        }

        gradleInstallationForTest.apply {
            val intTestImage: Sync by project.tasks
            gradleUserHomeDir.set(projectDirectory.dir("intTestHomeDir"))
            gradleGeneratedApiJarCacheDir.set(defaultGradleGeneratedApiJarCacheDirProvider())
            daemonRegistry.set(layout.buildDirectory.dir("daemon"))
            gradleHomeDir.set(dirWorkaround { intTestImage.destinationDir })
            gradleSnippetsDir.set(layout.projectDirectory.dir("subprojects/docs/src/snippets"))
            toolingApiShadedJarDir.set(dirWorkaround {
                // TODO Refactor to not reach into tasks of another project
                val toolingApiShadedJar: ShadedJar by project.rootProject.project(":toolingApi").tasks
                toolingApiShadedJar.jarFile.get().asFile.parentFile
            })
        }

        libsRepository.dir.set(projectDirectory.dir("build/repo"))

        binaryDistributions.apply {
            distsDir.set(layout.buildDirectory.dir(basePluginConvention.distsDirName))
            distZipVersion = project.version.toString()
        }
    }

    private
    fun DistributionTest.setJvmArgsOfTestJvm() {
        jvmArgs("-Xmx512m", "-XX:+HeapDumpOnOutOfMemoryError")
        if (!javaVersion.isJava8Compatible) {
            jvmArgs("-XX:MaxPermSize=768m")
        }
    }

    private
    fun DistributionTest.setSystemPropertiesOfTestJVM(project: Project) {
        // use -PtestVersions=all or -PtestVersions=1.2,1.3…
        val integTestVersionsSysProp = "org.gradle.integtest.versions"
        if (project.hasProperty("testVersions")) {
            systemProperties[integTestVersionsSysProp] = project.property("testVersions")
        } else {
            if (integTestVersionsSysProp !in systemProperties) {
                if (project.findProperty("testPartialVersions") == true) {
                    systemProperties[integTestVersionsSysProp] = "partial"
                }
                if (project.findProperty("testAllVersions") == true) {
                    systemProperties[integTestVersionsSysProp] = "all"
                }
                if (integTestVersionsSysProp !in systemProperties) {
                    systemProperties[integTestVersionsSysProp] = "default"
                }
            }
        }
    }
}


fun DistributionTest.defaultGradleGeneratedApiJarCacheDirProvider(): Provider<Directory> =
    project.rootProject.providers.provider { defaultGeneratedGradleApiJarCacheDir() }


/**
 * Computes a project and classpath specific `intTestHomeDir/generatedApiJars` directory.
 */
private
fun DistributionTest.defaultGeneratedGradleApiJarCacheDir(): Directory =
    project.rootProject.layout.projectDirectory.dir("intTestHomeDir/generatedApiJars/${project.version}/${project.name}-$classpathHash")


private
val DistributionTest.classpathHash
    get() = project.classPathHashOf(classpath)


private
fun Project.classPathHashOf(files: FileCollection) =
    serviceOf<ClasspathHasher>().hash(DefaultClassPath.of(files))
