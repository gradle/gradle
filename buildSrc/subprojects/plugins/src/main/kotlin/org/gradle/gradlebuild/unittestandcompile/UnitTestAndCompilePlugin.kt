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
import availableJavaInstallations
import library
import maxParallelForks
import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.build.ClasspathManifest
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.BuildEnvironment.agentNum
import org.gradle.gradlebuild.java.AvailableJavaInstallations
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.process.CommandLineArgumentProvider
import testLibrary
import java.util.concurrent.Callable
import java.util.jar.Attributes
import kotlin.reflect.full.declaredFunctions


enum class ModuleType(val compatibility: JavaVersion = JavaVersion.VERSION_1_8) {

    /**
     * the first modules loaded during startup that support an older Java version
     * to print a proper error message when running on an old JDK
     */
    STARTUP(JavaVersion.VERSION_1_6),

    /**
     * modules that may run as part of a worker process that supports an older JDK version
     */
    WORKER(JavaVersion.VERSION_1_6),

    /**
     * modules that end up in the distribution in addition to STARTUP and WORKER
     */
    CORE,

    /**
     * modules that are published as plugins on the portal
     */
    PORTAL_PLUGINS,

    /**
     * internal modules, like test fixtures, that are not part of the distribution
     */
    INTERNAL,
}


/**
 * By default, we run an extra build step ("GRADLE_RERUNNER") which runs all test classes failed in the previous build step ("GRADLE_RUNNER").
 * However, if previous test failures are too many (>10), this is probably not caused by flakiness.
 * In this case, we simply skip the GRADLE_RERUNNER step.
 */
const val tooManyTestFailuresThreshold = 10


class UnitTestAndCompilePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        apply(plugin = "groovy")

        val extension = extensions.create<UnitTestAndCompileExtension>("gradlebuildJava", this)

        base.archivesBaseName = "gradle-${name.replace(Regex("\\p{Upper}")) { "-${it.value.toLowerCase()}" }}"
        addDependencies()
        addGeneratedResources(extension)
        configureCompile()
        configureJarTasks()
        configureTests()
    }

    private
    fun Project.configureCompile() {
        afterEvaluate {
            val availableJavaInstallations = rootProject.the<AvailableJavaInstallations>()

            tasks.withType<JavaCompile>().configureEach {
                options.isIncremental = true
                configureCompileTask(this, options, availableJavaInstallations)
            }
            tasks.withType<GroovyCompile>().configureEach {
                groovyOptions.encoding = "utf-8"
                configureCompileTask(this, options, availableJavaInstallations)
            }
        }
        addCompileAllTask()
    }

    private
    fun configureCompileTask(compileTask: AbstractCompile, options: CompileOptions, availableJavaInstallations: AvailableJavaInstallations) {
        options.isFork = true
        options.encoding = "utf-8"
        options.compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
        val jdkForCompilation = availableJavaInstallations.javaInstallationForCompilation
        if (!jdkForCompilation.current) {
            options.forkOptions.javaHome = jdkForCompilation.javaHome
        }
        compileTask.inputs.property("javaInstallation", Callable {
            when (compileTask) {
                is JavaCompile -> jdkForCompilation
                else -> availableJavaInstallations.currentJavaInstallation
            }.vendorAndMajorVersion
        })
    }

    private
    fun Project.addGeneratedResources(gradlebuildJava: UnitTestAndCompileExtension) {
        val classpathManifest = tasks.register("classpathManifest", ClasspathManifest::class)
        java.sourceSets["main"].output.dir(mapOf("builtBy" to classpathManifest), gradlebuildJava.generatedResourcesDir)
        // Remove this IDEA import workaround once we completely migrated to the native IDEA import
        // See: https://github.com/gradle/gradle-private/issues/1675
        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    resourceDirs = resourceDirs + gradlebuildJava.generatedResourcesDir
                    testResourceDirs = testResourceDirs + gradlebuildJava.generatedTestResourcesDir
                }
            }
        }
    }

    private
    fun Project.addDependencies() {
        dependencies {
            val testImplementation = configurations.getByName("testImplementation")
            val testRuntimeOnly = configurations.getByName("testRuntimeOnly")
            testImplementation(library("junit"))
            testImplementation(library("groovy"))
            testImplementation(testLibrary("spock"))
            testRuntimeOnly(testLibrary("bytebuddy"))
            testRuntimeOnly(library("objenesis"))
        }
    }

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
            val baseVersion: String by rootProject.extra
            archiveVersion.set(baseVersion)
            manifest.attributes(mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to baseVersion))
        }
    }

    private
    fun Test.configureJvmForTest() {
        val javaInstallationForTest = project.rootProject.availableJavaInstallations.javaInstallationForTest
        jvmArgumentProviders.add(createCiEnvironmentProvider(this))
        executable = javaInstallationForTest.jvm.javaExecutable.absolutePath
        environment["JAVA_HOME"] = javaInstallationForTest.javaHome.absolutePath
        if (javaInstallationForTest.javaVersion.isJava7) {
            // enable class unloading
            jvmArgs("-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled")
        }
        if (javaInstallationForTest.javaVersion.isJava9Compatible) {
            // allow embedded executer to modify environment variables
            jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
            // allow embedded executer to inject legacy types into the system classloader
            jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        }
        // Includes JVM vendor and major version
        inputs.property("javaInstallation", Callable { javaInstallationForTest.vendorAndMajorVersion })
    }

    private
    fun Project.configureTests() {
        tasks.withType<Test>().configureEach {
            maxParallelForks = project.maxParallelForks

            configureJvmForTest()

            doFirst {
                if (BuildEnvironment.isCiServer) {
                    logger.lifecycle("maxParallelForks for '$path' is $maxParallelForks")
                }
            }
        }
    }

    private
    fun createCiEnvironmentProvider(test: Test): CommandLineArgumentProvider {
        return object : CommandLineArgumentProvider, Named {
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
        System.getenv("REPO_MIRROR_URLS")?.split(',')?.associate { nameToUrl ->
            val (name, url) = nameToUrl.split(':', limit = 2)
            name to url
        } ?: emptyMap()
}


open class UnitTestAndCompileExtension(val project: Project) {
    val generatedResourcesDir = project.file("${project.buildDir}/generated-resources/main")
    val generatedTestResourcesDir = project.file("${project.buildDir}/generated-resources/test")
    var moduleType: ModuleType? = null
        set(value) {
            field = value!!
            project.addPlatformDependency(value)
            project.java.targetCompatibility = value.compatibility
            project.java.sourceCompatibility = value.compatibility
            project.java.disableAutoTargetJvmGradle53()
        }

    init {
        project.afterEvaluate {
            if (this@UnitTestAndCompileExtension.moduleType == null) {
                throw InvalidUserDataException("gradlebuild.moduletype must be set for project $project")
            }
        }
    }

    private
    fun Project.addPlatformDependency(moduleType: ModuleType) {
        val platformProject = ":distributionsDependencies"
        dependencies {
            if (moduleType == ModuleType.PORTAL_PLUGINS) {
                "compileOnly"(platform(project(platformProject)))
                "testImplementation"(platform(project(platformProject)))
            } else {
                "implementation"(platform(project(platformProject)))
            }
        }
    }
}


fun JavaPluginConvention.disableAutoTargetJvmGradle53() {
    val function = JavaPluginConvention::class.declaredFunctions.find { it.name == "disableAutoTargetJvm" }
    function?.also {
        it.call(this)
    }
}
