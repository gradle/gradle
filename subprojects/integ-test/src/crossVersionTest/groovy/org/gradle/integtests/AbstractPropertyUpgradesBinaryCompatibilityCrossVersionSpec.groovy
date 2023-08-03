/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.integtests

import org.gradle.configuration.DefaultImportsReader
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.internal.lazy.Lazy
import java.util.function.Supplier

/**
 * Tests that task classes with property upgrades are compiled against one version of Gradle are compatible with another version.
 */
abstract class AbstractPropertyUpgradesBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {

    private static final List<String> DEFAULT_JAVA_IMPORTS = [
        "java.lang.*",
        "java.util.*",
        "java.util.concurrent.*",
        "java.util.regex.*",
        "java.util.function.*",
        "java.lang.reflect.*",
        "java.io.*",
        "javax.inject.Inject"
    ]

    /**
     * These packages are not available with the version we are building a plugin with and will cause plugin compilation to fail.
     */
    private static final List<String> BLACKLISTED_PACKAGES = [
        // Testkit is not available on compile classpath for plugin
        "org.gradle.testkit",
        // Flow API is not available in Gradle 8.0.2 that we use to produce a plugin
        "org.gradle.api.flow",
    ]

    private static final Supplier<List<String>> DEFAULT_GRADLE_IMPORTS = Lazy.unsafe().of {
        return new DefaultImportsReader().importPackages.findAll {
            !BLACKLISTED_PACKAGES.any { packageName -> it.startsWith(packageName) }
        }.collect { it + ".*" }
    }

    protected List<Class<?>> additionalImportedClasses() {
        return []
    }

    protected List<String> additionalImports() {
        return []
    }

    protected List<String> getDefaultImports() {
        return DEFAULT_GRADLE_IMPORTS.get() + DEFAULT_JAVA_IMPORTS
    }

    protected void prepareGroovyPluginTest(String pluginApplyBody) {
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                implementation gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            ${getDefaultImports().collect { "import " + it }.join("\n")}
            ${additionalImportedClasses().collect { "import " + it.name }.join("\n")}
            ${additionalImports().collect { "import " + it }.join("\n")}

            class SomePlugin implements Plugin<Project> {
                void apply(Project project) {
                    $pluginApplyBody
                }
            }
            """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            apply plugin: SomePlugin
        """
    }

    protected void prepareJavaPluginTest(String pluginApplyBody) {
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                implementation gradleApi()
            }
        """

        file("producer/src/main/java/SomePlugin.java") << """
            ${getDefaultImports().collect { "import " + it + ";" }.join("\n")}
            ${additionalImportedClasses().collect { "import " + it.name + ";" }.join("\n")}
            ${additionalImports().collect { "import " + it }.join("\n")}

            class SomePlugin implements Plugin<Project> {
                public void apply(Project project) {
                    $pluginApplyBody
                }
            }
            """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            apply plugin: SomePlugin
        """
    }

    protected void prepareKotlinPluginTest(String pluginApplyBody) {
        file("producer/build.gradle.kts") << """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                mavenCentral()
            }
        """

        file("producer/src/main/kotlin/SomePlugin.kt") << """
            ${getDefaultImports().collect { "import " + it }.join("\n")}
            ${additionalImportedClasses().collect { "import " + it.name }.join("\n")}
            ${additionalImports().collect { "import " + it }.join("\n")}

            class SomePlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    $pluginApplyBody
                }
            }
            """

        buildFile << """
            buildscript {
                dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
            }

            apply plugin: SomePlugin
        """
    }
}

