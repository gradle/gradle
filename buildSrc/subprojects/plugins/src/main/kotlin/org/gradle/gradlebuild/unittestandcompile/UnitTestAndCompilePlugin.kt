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
package org.gradle.gradlebuild.unittestandcompile

import accessors.base
import accessors.java
import accessors.groovy
import buildJvms
import libraries
import library
import maxParallelForks
import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.BuildEnvironment.agentNum
import org.gradle.gradlebuild.java.AvailableJavaInstallationsPlugin
import org.gradle.gradlebuild.java.JavaInstallation
import org.gradle.gradlebuild.packaging.ClasspathManifest
import org.gradle.gradlebuild.versioning.buildVersion
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testretry.TestRetryPlugin
import testLibrary
import java.util.concurrent.Callable
import java.util.jar.Attributes
import org.gradle.testing.PerformanceTest


@Suppress("unused")
class UnitTestAndCompilePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        apply(plugin = "groovy")
        plugins.apply(AvailableJavaInstallationsPlugin::class.java)
        plugins.apply(TestRetryPlugin::class.java)

        val extension = extensions.create<UnitTestAndCompileExtension>("gradlebuildJava", this)

        base.archivesBaseName = "gradle-${name.replace(Regex("\\p{Upper}")) { "-${it.value.toLowerCase()}" }}"
        addDependencies()
        configureClasspathManifestGeneration(extension)
        configureCompile()
        configureSourcesVariant()
        configureJarTasks()
        configureTests()
    }

    private
    fun Project.configureCompile() {
        java.targetCompatibility = JavaVersion.VERSION_1_8
        java.sourceCompatibility = JavaVersion.VERSION_1_8
        afterEvaluate {
            val jdkForCompilation = buildJvms.compileJvm.get()

            tasks.withType<JavaCompile>().configureEach {
                configureCompileTask(this, options, jdkForCompilation)
            }
            tasks.withType<GroovyCompile>().configureEach {
                groovyOptions.encoding = "utf-8"
                configureCompileTask(this, options, jdkForCompilation)
            }
        }
        addCompileAllTask()
    }

    private
    fun Project.configureSourcesVariant() {
        the<JavaPluginExtension>().apply {
            withSourcesJar()
        }
        val implementation by configurations

        @Suppress("unused_variable")
        val transitiveSourcesElements by configurations.creating {
            isCanBeResolved = false
            isCanBeConsumed = true
            extendsFrom(implementation)
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named("gradle-source-folders"))
            }
            val sourceSet = the<SourceSetContainer>()[SourceSet.MAIN_SOURCE_SET_NAME]
            sourceSet.java.srcDirs.forEach {
                outgoing.artifact(it)
            }
            sourceSet.groovy.srcDirs.forEach {
                outgoing.artifact(it)
            }
        }
    }

    private
    fun configureCompileTask(compileTask: AbstractCompile, options: CompileOptions, jdkForCompilation: JavaInstallation) {
        options.isFork = true
        options.encoding = "utf-8"
        options.isIncremental = true
        options.forkOptions.jvmArgs?.add("-XX:+HeapDumpOnOutOfMemoryError")
        options.forkOptions.memoryMaximumSize = "1g"
        options.compilerArgs.addAll(mutableListOf("-Xlint:-options", "-Xlint:-path"))
        if (!jdkForCompilation.current) {
            options.forkOptions.javaHome = jdkForCompilation.javaHome
        }
        compileTask.inputs.property("javaInstallation", Callable {
            jdkForCompilation.vendorAndMajorVersion
        })
    }

    private
    fun Project.configureClasspathManifestGeneration(gradlebuildJava: UnitTestAndCompileExtension) {
        val runtimeClasspath by configurations
        val classpathManifest = tasks.register("classpathManifest", ClasspathManifest::class) {
            this.runtimeClasspath.from(runtimeClasspath)
            this.externalDependencies.from(runtimeClasspath.fileCollection { it is ExternalDependency })
            this.manifestFile.set(gradlebuildJava.generatedResourcesDir.file("${base.archivesBaseName}-classpath.properties"))
        }
        java.sourceSets["main"].output.dir(gradlebuildJava.generatedResourcesDir, "builtBy" to classpathManifest)
    }

    private
    fun Project.addDependencies() {
        if (libraries.isEmpty()) {
            return
        }
        val platformProject = ":distributionsDependencies"
        dependencies {
            val implementation = configurations.getByName("implementation")
            val compileOnly = configurations.getByName("compileOnly")
            val testImplementation = configurations.getByName("testImplementation")
            val testCompileOnly = configurations.getByName("testCompileOnly")
            val testRuntimeOnly = configurations.getByName("testRuntimeOnly")
            testImplementation(platform(project(platformProject)))
            testCompileOnly(library("junit"))
            testRuntimeOnly(library("junit5_vintage"))
            testImplementation(library("groovy"))
            testImplementation(testLibrary("spock"))
            testRuntimeOnly(testLibrary("bytebuddy"))
            testRuntimeOnly(library("objenesis"))

            compileOnly(platform(project(platformProject)))

            implementation.withDependencies {
                if (!isPublishedIndependently()) {
                    "implementation"(platform(project(platformProject)))
                }
            }
        }
    }

    private
    fun Project.isPublishedIndependently() = name != "toolingApi" &&
        (pluginManager.hasPlugin("gradlebuild.portalplugin.kotlin") || pluginManager.hasPlugin("gradlebuild.publish-public-libraries"))

    private
    fun Project.addCompileAllTask() {
        tasks.register("compileAll") {
            val compileTasks = project.tasks.matching {
                it is JavaCompile || it is GroovyCompile
            }
            dependsOn(compileTasks)
        }
    }

    private
    fun Project.configureJarTasks() {
        tasks.withType<Jar>().configureEach {
            val baseVersion = rootProject.buildVersion.baseVersion
            archiveVersion.set(baseVersion)
            manifest.attributes(mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to baseVersion))
        }
    }

    private
    fun Test.configureJvmForTest() {
        val jvmForTest = project.buildJvms.testJvm.get()

        jvmArgumentProviders.add(createCiEnvironmentProvider(this))
        executable = jvmForTest.javaExecutable.absolutePath
        environment["JAVA_HOME"] = jvmForTest.javaHome.absolutePath
        if (jvmForTest.javaVersion.isJava7) {
            // enable class unloading
            jvmArgs("-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled")
        }
        if (jvmForTest.javaVersion.isJava9Compatible) {
            // allow embedded executer to modify environment variables
            jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
            // allow embedded executer to inject legacy types into the system classloader
            jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        }
        // Includes JVM vendor and major version
        inputs.property("javaInstallation", Callable { jvmForTest.vendorAndMajorVersion })
    }

    private
    fun Test.addOsAsInputs() {
        // Add OS as inputs since tests on different OS may behave differently https://github.com/gradle/gradle-private/issues/2831
        // the version currently differs between our dev infrastructure, so we only track the name and the architecture
        inputs.property("operatingSystem", "${OperatingSystem.current().name} ${System.getProperty("os.arch")}")
    }

    private
    fun Project.configureTests() {
        normalization {
            runtimeClasspath {
                // Ignore the build receipt as it is not relevant for tests and changes between each execution
                ignore("org/gradle/build-receipt.properties")
            }
        }
        tasks.withType<Test>().configureEach {
            maxParallelForks = project.maxParallelForks

            if (!BuildEnvironment.isIntelliJIDEA) {
                // JUnit 5 Vintage engine can't recognize Spock @Unroll test method correctly
                // So if running an @Unroll method in IDEA with include pattern "SomeClass.methodName"
                // The result will be incorrect. In this case we fallback to JUnit
                useJUnitPlatform()
            }
            configureJvmForTest()
            addOsAsInputs()

            if (BuildEnvironment.isCiServer && this !is PerformanceTest) {
                retry {
                    maxRetries.set(1)
                    maxFailures.set(10)
                }
                doFirst {
                    logger.lifecycle("maxParallelForks for '$path' is $maxParallelForks")
                }
            }
        }
    }

    private
    fun createCiEnvironmentProvider(test: Test): CommandLineArgumentProvider {
        return object : CommandLineArgumentProvider, Named {
            @Internal
            override fun getName() = "ciEnvironment"

            override fun asArguments(): Iterable<String> {
                return if (BuildEnvironment.isCiServer) {
                    getRepoMirrorSystemProperties() + mapOf(
                        "org.gradle.test.maxParallelForks" to test.maxParallelForks,
                        "org.gradle.ci.agentCount" to 2,
                        "org.gradle.ci.agentNum" to agentNum
                    ).map {
                        "-D${it.key}=${it.value}"
                    }
                } else {
                    listOf()
                }
            }
        }
    }

    private
    fun getRepoMirrorSystemProperties(): List<String> = collectMirrorUrls().map {
        "-Dorg.gradle.integtest.mirrors.${it.key}=${it.value}"
    }

    private
    fun collectMirrorUrls(): Map<String, String> =
        // expected env var format: repo1_id:repo1_url,repo2_id:repo2_url,...
        System.getenv("REPO_MIRROR_URLS")?.ifBlank { null }?.split(',')?.associate { nameToUrl ->
            val (name, url) = nameToUrl.split(':', limit = 2)
            name to url
        } ?: emptyMap()
}


open class UnitTestAndCompileExtension(val project: Project) {
    val generatedResourcesDir = project.objects.directoryProperty().convention(project.layout.buildDirectory.dir("generated-resources/main"))

    fun usedInWorkers() {
        project.java.targetCompatibility = JavaVersion.VERSION_1_6
        project.java.sourceCompatibility = JavaVersion.VERSION_1_6
        project.java.disableAutoTargetJvm()
    }

    fun usedForStartup() {
        project.java.targetCompatibility = JavaVersion.VERSION_1_6
        project.java.sourceCompatibility = JavaVersion.VERSION_1_6
        project.java.disableAutoTargetJvm()
    }
}
