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
            configureGradleTestEnvironment(
                rootProject.providers,
                rootProject.layout,
                rootProject.base,
                rootProject.objects
            )
            addSetUpAndTearDownActions(project)
        }
        rootProject.project(":toolingApi").afterEvaluate {
            this@run.tasks.withType<DistributionTest>().configureEach {
                gradleInstallationForTest.toolingApiShadedJarDir.set(dirWorkaround(providers, layout, objects) {
                    // TODO Refactor to not reach into tasks of another project
                    val toolingApiShadedJar: ShadedJar by tasks
                    toolingApiShadedJar.jarFile.get().asFile.parentFile
                })
            }
        }
    }

    // TODO: Replace this with something in the Gradle API to make this transition easier
    private
    fun dirWorkaround(
        providers: ProviderFactory,
        layout: ProjectLayout,
        objects: ObjectFactory,
        directory: () -> File
    ): Provider<Directory> =
        objects.directoryProperty().also {
            it.set(layout.projectDirectory.dir(providers.provider { directory().absolutePath }))
        }

    private
    fun DistributionTest.addSetUpAndTearDownActions(project: Project) {
        val cleanupExtension = project.rootProject.extensions.getByType(CleanupExtension::class.java)
        this.tracker.set(cleanupExtension.tracker)
    }

    private
    fun DistributionTest.configureGradleTestEnvironment(providers: ProviderFactory, layout: ProjectLayout, basePluginConvention: BasePluginConvention, objects: ObjectFactory) {

        val projectDirectory = layout.projectDirectory

        gradleInstallationForTest.apply {
            val intTestImage: Sync by project.tasks
            gradleUserHomeDir.set(projectDirectory.dir("intTestHomeDir"))
            gradleGeneratedApiJarCacheDir.set(defaultGradleGeneratedApiJarCacheDirProvider(providers, layout))
            daemonRegistry.set(layout.buildDirectory.dir("daemon"))
            gradleHomeDir.set(dirWorkaround(providers, layout, objects) { intTestImage.destinationDir })
            gradleSnippetsDir.set(layout.projectDirectory.dir("subprojects/docs/src/snippets"))
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
        val sysPropValue = System.getProperty(integTestVersionsSysProp)
        if (project.hasProperty("testVersions")) {
            systemProperties[integTestVersionsSysProp] = project.property("testVersions")
        } else {
            systemProperties[integTestVersionsSysProp] = "default"
        }
    }
}


fun DistributionTest.defaultGradleGeneratedApiJarCacheDirProvider(providers: ProviderFactory, layout: ProjectLayout): Provider<Directory> {
    val projectName = project.name
    val projectVersion = project.version
    val classpathHasher = project.serviceOf<ClasspathHasher>()
    return providers.provider { defaultGeneratedGradleApiJarCacheDir(layout, projectName, projectVersion, classpathHasher) }
}


/**
 * Computes a project and classpath specific `intTestHomeDir/generatedApiJars` directory.
 */
private
fun DistributionTest.defaultGeneratedGradleApiJarCacheDir(
    layout: ProjectLayout,
    projectName: String,
    projectVersion: Any,
    classpathHasher: ClasspathHasher
): Directory =
    layout.projectDirectory.dir("intTestHomeDir/generatedApiJars/$projectVersion/$projectName-${classpathHash(classpathHasher)}")


private
fun DistributionTest.classpathHash(classpathHasher: ClasspathHasher) =
    classpathHasher.hash(DefaultClassPath.of(classpath))
