/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.jvm

import gradlebuild.identity.extension.GradleModuleExtension
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A container of [JvmCompilation]s for a JVM project.
 *
 * Utility methods are provided to add compilations based on existing [SourceSet]s.
 */
abstract class JvmCompileExtension {

    companion object {
        const val NAME: String = "jvmCompile"
    }

    abstract val compilations: NamedDomainObjectContainer<JvmCompilation>

    private val multiReleaseTestTaskActions = mutableListOf<Action<TaskProvider<Test>>>()
    private var anyMultiReleaseTestTaskRegistered = false

    /**
     * Registers [action] to be invoked for every multi-release versioned test task created by
     * [addMultiReleaseVersion]. This lets CI lifecycle wiring attach these tasks to its
     * aggregate tasks without this class needing to know which lifecycle tasks exist.
     *
     * Must be called before the first [addMultiReleaseVersion]; registering afterwards would
     * miss already-created tasks, so it fails fast instead of silently doing so.
     */
    fun whenMultiReleaseTestTaskRegistered(action: Action<TaskProvider<Test>>) {
        check(!anyMultiReleaseTestTaskRegistered) {
            "whenMultiReleaseTestTaskRegistered { } must be called before addMultiReleaseVersion()"
        }
        multiReleaseTestTaskActions.add(action)
    }

    fun Project.addCompilationFrom(sourceSet: NamedDomainObjectProvider<SourceSet>): JvmCompilation {
        return addCompilationFrom(sourceSet.get())
    }

    fun Project.addCompilationFrom(sourceSet: NamedDomainObjectProvider<SourceSet>, configure: JvmCompilation.() -> Unit): JvmCompilation {
        return addCompilationFrom(sourceSet.get(), configure)
    }

    fun Project.addCompilationFrom(sourceSet: SourceSet): JvmCompilation {
        return compilations.create(sourceSet.name) {
            from(sourceSet)
        }
    }

    fun Project.addCompilationFrom(sourceSet: SourceSet, configure: JvmCompilation.() -> Unit): JvmCompilation {
        return compilations.create(sourceSet.name) {
            from(sourceSet)
            configure()
        }
    }

    /**
     * Adds a Java [version]-specific compilation that is packaged into the project's JAR as a
     * Multi-Release JAR entry under `META-INF/versions/<version>`.
     *
     * Sources live in `src/main/java<version>`. A class there overrides the base class of the
     * same fully-qualified name when the JAR is run on a Java [version]+ runtime, while the
     * base `src/main/java` class remains the fallback for the production target version.
     *
     * The versioned classes are only selected when loaded from the assembled multi-release
     * JAR, never from `build/classes`. To keep the versioned classes testable without leaking
     * their (duplicate-named) output onto the main test classpath, a dedicated
     * `java<version>Test` source set and matching `Test` task are created. Tests that need the
     * versioned classes live in `src/test/java<version>/{java,groovy}`.
     *
     * @param version the Java version of the multi-release classes to add; must be greater than the production target JVM version of this project
     * @param testJvmVersion the Java version to run the tests that exercise the multi-release classes on
     */
    fun Project.addMultiReleaseVersion(version: Int, testJvmVersion: Int) {
        val productionTarget = the<GradleModuleExtension>().computedRuntimes.computeProductionJvmTargetVersion().get()
        require(version > productionTarget) {
            "Multi-release version ($version) must be greater than the production target JVM version " +
                "($productionTarget); otherwise the base classes already cover it."
        }
        require(testJvmVersion >= version) {
            "Test JVM version ($testJvmVersion) must be at least the multi-release version ($version) " +
                "to be able to run the tests that exercise the multi-release classes."
        }
        val sourceSets = the<SourceSetContainer>()
        val main = sourceSets.getByName("main")
        val test = sourceSets.getByName("test")

        val versioned = setUpMainMultiReleaseSourceSet(version, main)

        val versionedTest = setUpTestMultiReleaseSourceSet(version, main, test, versioned, testJvmVersion)
        val versionedTestTask = tasks.register<Test>(versionedTest.name) {
            // The JDK used may differ from the version of the multi-release classes, but we accept that minor confusion
            // in order to not make structuring this more convoluted.
            description = "Runs tests that exercise the Java $version multi-release classes on JDK $testJvmVersion"
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            testClassesDirs = versionedTest.output.classesDirs
            classpath = versionedTest.runtimeClasspath
            javaLauncher = project.the<JavaToolchainService>().launcherFor {
                languageVersion = JavaLanguageVersion.of(testJvmVersion)
            }
            shouldRunAfter(tasks.named("test"))
        }
        anyMultiReleaseTestTaskRegistered = true
        multiReleaseTestTaskActions.forEach { it.execute(versionedTestTask) }
    }

    private fun Project.setUpMainMultiReleaseSourceSet(version: Int, main: SourceSet): SourceSet {
        val versioned = the<SourceSetContainer>().create("java$version") {
            java.setSrcDirs(listOf("src/main/java$version"))
            compileClasspath += main.output + main.compileClasspath
        }
        compilations.create(versioned.name) {
            targetJvmVersion.set(version)
            // A versioned source set only contains Java sources. Associate just the Java
            // compilation so we don't force a (possibly unavailable) toolchain for the
            // auto-created groovy/kotlin compile tasks of this source set.
            associate(tasks.named<JavaCompile>(versioned.getCompileTaskName("java")))
        }
        tasks.named<Jar>("jar") {
            into("META-INF/versions/$version") {
                from(versioned.output)
            }
            manifest {
                attributes(mapOf("Multi-Release" to "true"))
            }
        }
        return versioned
    }

    private fun Project.setUpTestMultiReleaseSourceSet(
        version: Int,
        main: SourceSet,
        test: SourceSet,
        versioned: SourceSet,
        testJvmVersion: Int
    ): SourceSet {
        fun Configuration.artifactsForTestJvm(): FileCollection {
            val jvmVersion = attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)
            return incoming.artifactView {
                lenient(false)
                if (jvmVersion != null) {
                    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, jvmVersion)
                }
            }.files
        }

        val versionedTest = the<SourceSetContainer>().create("java${version}Test") {
            java.setSrcDirs(listOf("src/java${version}Test/java"))
            extensions.getByType(GroovySourceDirectorySet::class.java)
                .setSrcDirs(listOf("src/java${version}Test/groovy"))
            val testCompileArtifacts = configurations.getByName(test.compileClasspathConfigurationName)
                .artifactsForTestJvm()
            val testRuntimeArtifacts = configurations.getByName(test.runtimeClasspathConfigurationName)
                .artifactsForTestJvm()
            compileClasspath = files(versioned.output, main.output, test.output, testCompileArtifacts)
            runtimeClasspath = files(output, versioned.output, main.output, test.output, testRuntimeArtifacts)
        }

        addCompilationFrom(versionedTest) {
            targetJvmVersion.set(testJvmVersion)
        }
        tasks.named("check") {
            dependsOn(versionedTest)
        }
        return versionedTest
    }

}
