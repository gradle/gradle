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

package org.gradle.smoketests

import org.gradle.internal.reflect.validation.ValidationMessageChecker

/**
 * Base class for smoke tests for Kotlin and Kotlin Multiplatform plugins.
 */
abstract class AbstractKotlinPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker, RunnerFactory {
    @Override
    void configureValidation(String testedPluginId, String version) {
        validatePlugins {
            if (isAndroidKotlinPlugin(testedPluginId)) {
                buildFile << """
                    android {
                        namespace = "org.gradle.smoke.test"
                        compileSdk = 24
                        buildToolsVersion = '${AGP_VERSIONS.getBuildToolsVersionFor(AGP_VERSIONS.latestStable)}'
                    }
                """
            }
            if (testedPluginId == 'org.jetbrains.kotlin.js') {
                buildFile << """
                    kotlin { js(IR) { browser() } }
                """
            }
            if (testedPluginId == 'org.jetbrains.kotlin.multiplatform') {
                buildFile << """
                    kotlin {
                        jvm()
                        js(IR) { browser() }
                    }
                """
            }

            alwaysPasses()

            settingsFile << """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                    }
                }
            """
        }
    }

    protected boolean isAndroidKotlinPlugin(String pluginId) {
        return pluginId.contains('android')
    }

    protected static class KotlinDeprecations extends BaseDeprecations implements WithKotlinDeprecations {
        KotlinDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }
    }
}
