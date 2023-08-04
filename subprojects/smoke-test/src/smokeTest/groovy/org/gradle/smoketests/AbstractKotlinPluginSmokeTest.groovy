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

import org.gradle.api.problems.interfaces.Severity
import org.gradle.internal.reflect.validation.ValidationMessageChecker

/**
 * Base class for smoke tests for Kotlin and Kotlin Multiplatform plugins.
 */
abstract class AbstractKotlinPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker, KotlinRunnerFactory {
    @Override
    void configureValidation(String testedPluginId, String version) {
        validatePlugins {
            if (isAndroidKotlinPlugin(testedPluginId)) {
                buildFile << """
                    android {
                        namespace = "org.gradle.smoke.test"
                        compileSdkVersion 24
                        buildToolsVersion '${TestedVersions.androidTools}'
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

            /*
             * Register validation failures due to unsupported nested types
             * The issue picked up by validation was fixed in Kotlin 1.7.2,
             * see https://youtrack.jetbrains.com/issue/KT-51532
             */
            if (version == '1.7.0') {
                // Register validation failure for plugin itself (or jvm plugin respectively)
                if (testedPluginId in ['org.jetbrains.kotlin.kapt', 'org.jetbrains.kotlin.plugin.scripting']) {
                    onPlugins(['org.jetbrains.kotlin.jvm']) { registerValidationFailure(delegate) }
                } else {
                    onPlugin(testedPluginId) { registerValidationFailure(delegate) }
                }
                // Register validation failures for plugins brought in by this plugin
                if (testedPluginId in ['org.jetbrains.kotlin.android', 'org.jetbrains.kotlin.android.extensions']) {
                    onPlugins(['com.android.application',
                               'com.android.build.gradle.api.AndroidBasePlugin',
                               'com.android.internal.application',
                               'com.android.internal.version-check']) { alwaysPasses() }
                }
                if (testedPluginId == 'org.jetbrains.kotlin.jvm'
                        || testedPluginId == 'org.jetbrains.kotlin.multiplatform'
                        || testedPluginId == 'org.jetbrains.kotlin.kapt'
                        || testedPluginId == 'org.jetbrains.kotlin.plugin.scripting') {
                    onPlugins(['org.jetbrains.kotlin.gradle.scripting.internal.ScriptingGradleSubplugin',
                               'org.jetbrains.kotlin.gradle.scripting.internal.ScriptingKotlinGradleSubplugin',
                    ]) { registerValidationFailure(delegate) }
                }
                if (testedPluginId == 'org.jetbrains.kotlin.js'
                        || testedPluginId == 'org.jetbrains.kotlin.multiplatform') {
                    onPlugins(['org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin',
                               'org.jetbrains.kotlin.gradle.targets.js.npm.NpmResolverPlugin',
                               'org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin'
                    ]) { registerValidationFailure(delegate) }
                }
                if (testedPluginId == 'org.jetbrains.kotlin.kapt') {
                    onPlugin('kotlin-kapt') { registerValidationFailure(delegate) }
                }
            } else {
                alwaysPasses()
            }

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

    protected registerValidationFailure(org.gradle.smoketests.WithPluginValidation.PluginValidation pluginValidation) {
        pluginValidation.failsWith(nestedTypeUnsupported {
            type('org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest')
                    .property('environment')
                    .annotatedType('java.lang.String')
                    .reason('Nested types are expected to either declare some annotated properties or some behaviour that requires capturing the type as input')
                    .includeLink()
        }, Severity.WARNING)
    }

    protected static class KotlinDeprecations extends BaseDeprecations implements WithKotlinDeprecations {
        KotlinDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }
    }
}
