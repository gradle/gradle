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


import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec

/**
 * Tests that task classes with property upgrades are compiled against one version of Gradle are compatible with another version.
 */
abstract class AbstractPropertyUpgradesBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {

    protected List<Class<?>> importClasses() {
        return []
    }

    protected void prepareGroovyPluginTest(String pluginApplyBody) {
        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                implementation gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            ${importClasses().collect { "import " + it.name }.join("\n")}

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
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            ${importClasses().collect { "import " + it.name + ";" }.join("\\n")}

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
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            ${importClasses().collect { "import " + it.name }.join("\\n")}

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

