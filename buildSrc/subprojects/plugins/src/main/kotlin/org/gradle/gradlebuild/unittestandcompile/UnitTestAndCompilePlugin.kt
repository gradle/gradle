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
import org.gradle.api.JavaVersion
import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.build.ClasspathManifest
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.BuildEnvironment.agentNum
import org.gradle.internal.jvm.Jvm
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.compile.GradleCompilePlugin
import org.gradle.process.CommandLineArgumentProvider
import testLibraries
import testLibrary
import java.util.jar.Attributes

class UnitTestAndCompilePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        apply { plugin("groovy") }
        apply { plugin(GradleCompilePlugin::class.java) }

        val extension = extensions.create<UnitTestAndCompileExtension>("gradlebuildJava", this)

        base.archivesBaseName = "gradle-${name.replace(Regex("\\p{Upper}")) { "-${it.value.toLowerCase()}" }}"
        java.sourceCompatibility = JavaVersion.VERSION_1_7
        addDependencies()
        addGeneratedResources(extension)
        configureJarTasks()
        addCompileAllTask()
        configureTests()
    }

    private fun Project.addGeneratedResources(gradlebuildJava: UnitTestAndCompileExtension) {
        val classpathManifest by tasks.creating(ClasspathManifest::class)
        java.sourceSets["main"].output.dir(mapOf("builtBy" to classpathManifest), gradlebuildJava.generatedResourcesDir)
    }

    private fun Project.addDependencies() {
        dependencies {
            val testCompile by configurations
            testCompile(library("junit"))
            testCompile(library("groovy"))
            testCompile(testLibrary("spock"))
            testLibraries("jmock").forEach { testCompile(it) }

            components {
                withModule("org.spockframework:spock-core") {
                    allVariants {
                        withDependencyConstraints {
                            filter { it.group == "org.objenesis" }.forEach {
                                it.version { prefer("1.2") }
                                it.because("1.2 is required by Gradle and part of the distribution")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Project.addCompileAllTask() {
        tasks.create("compileAll") {
            val compileTasks = project.tasks.matching {
                it is JavaCompile || it is GroovyCompile
            }
            dependsOn(compileTasks)
        }
    }

    private fun Project.configureJarTasks() {
        tasks.withType<Jar>().all {
            version = rootProject.extra["baseVersion"] as String
            manifest.attributes(mapOf(
                    Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                    Attributes.Name.IMPLEMENTATION_VERSION.toString() to version))
        }
    }

    private fun Project.configureTests() {
        val javaInstallationForTest = rootProject.availableJavaInstallations.javaInstallationForTest

        tasks.withType<Test>().all {
            maxParallelForks = project.maxParallelForks
            jvmArgumentProviders.add(createCiEnvironmentProvider(this))
            executable = Jvm.forHome(javaInstallationForTest.javaHome).javaExecutable.absolutePath
            environment["JAVA_HOME"] = javaInstallationForTest.javaHome.absolutePath
            if (javaInstallationForTest.javaVersion.isJava7) {
                // enable class unloading
                jvmArgs("-XX:+UseConcMarkSweepGC", "-XX:+CMSClassUnloadingEnabled")
            }
            // Includes JVM vendor and major version
            inputs.property("javaInstallation", javaInstallationForTest.displayName)
            doFirst {
                if (BuildEnvironment.isCiServer) {
                    println("maxParallelForks for '$path' is $maxParallelForks")
                }
            }
        }
    }

    private fun createCiEnvironmentProvider(test: Test) : CommandLineArgumentProvider {
        return object : CommandLineArgumentProvider, Named {
            override fun getName() = "ciEnvironment"

            override fun asArguments(): Iterable<String> {
                return if (BuildEnvironment.isCiServer) {
                    mapOf(
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
}

open class UnitTestAndCompileExtension(project: Project) {
    val generatedResourcesDir = project.file("${project.buildDir}/generated-resources/main")
    val generatedTestResourcesDir = project.file("${project.buildDir}/generated-resources/test")
}
