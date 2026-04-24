/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal

import groovy.transform.SelfType
import org.gradle.features.internal.builders.TestScenarioBuilder
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * A trait that integration tests mix in to use the compositional test scenario DSL.
 *
 * <p>{@link #testScenario} returns a {@link PluginBuilder} that has been configured with all
 * generated source files and plugin IDs. Call {@code .prepareToExecute()} on the result
 * to generate the build script and plugin descriptors.</p>
 *
 * <p>Also provides static helper properties for common settings/build script content
 * ({@link #getPluginsFromIncludedBuild()}, {@link #getPluginBuildScriptForJava()},
 * {@link #getPluginBuildScriptForKotlin()}).</p>
 *
 * <p>All generated plugin source files are written to a "plugins" included build directory
 * within the test's temporary project directory.</p>
 */
@SelfType(AbstractIntegrationSpec)
trait TestScenarioFixture {

    /**
     * Builds a complete test scenario from the given DSL configuration.
     *
     * <p>If no project types are declared, a default "testProjectType" is added automatically.</p>
     *
     * @param config closure delegating to {@link TestScenarioBuilder}
     * @return the configured PluginBuilder with all generated source files
     */
    PluginBuilder testScenario(
        @DelegatesTo(value = TestScenarioBuilder, strategy = Closure.DELEGATE_FIRST)
        Closure config
    ) {
        def scenario = new TestScenarioBuilder()
        config.delegate = scenario
        config.resolveStrategy = Closure.DELEGATE_FIRST
        config.call()

        return scenario.build(file("plugins"))
    }

    /**
     * Returns the settings script content that includes the "plugins" build and applies the ecosystem plugin.
     */
    static String getPluginsFromIncludedBuild() {
        return """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("com.example.test-software-ecosystem")
            }
        """
    }

    /**
     * Returns build script content that configures Java compilation to target Java 8.
     */
    static String getPluginBuildScriptForJava() {
        return """

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """
    }

    /**
     * Returns build script content that configures Kotlin compilation to target JVM 8,
     * along with the Java compilation settings.
     */
    static String getPluginBuildScriptForKotlin() {
        return """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            repositories {
                mavenCentral()
            }

            kotlin {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
            }

            ${pluginBuildScriptForJava}
        """
    }
}
