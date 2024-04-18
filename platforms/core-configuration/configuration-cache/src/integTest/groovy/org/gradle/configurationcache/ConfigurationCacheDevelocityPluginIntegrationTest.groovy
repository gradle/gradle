/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import groovy.test.NotYetImplemented
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.KotlinDslTestUtil
import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin
import spock.lang.Issue

class ConfigurationCacheDevelocityPluginIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def setup() {
        settingsFile '''
            pluginManagement {
                includeBuild("dv-conventions")
            }
            plugins {
                id("dv-conventions")
            }
        '''

        buildFile '''
            plugins { id("java") }
        '''
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/19047")
    def "problem is reported for Kotlin lambda expression with develocity plugin"() {
        given:
        createDir('dv-conventions') {
            file('src/main/kotlin/my/DvConventionsPlugin.kt') << """
                package my

                import org.gradle.api.*
                import org.gradle.kotlin.dsl.*

                class DvConventionsPlugin : Plugin<$Settings.name> {
                    override fun apply(settings: $Settings.name) {
                        settings.apply(mapOf("plugin" to "com.gradle.develocity"))
                        settings.develocity {
                            buildScan.obfuscation.hostname {
                                // lambda expression
                                "unset"
                            }
                        }
                    }
                }
            """
            file('build.gradle.kts') << """
                plugins {
                    `embedded-kotlin`
                    `java-gradle-plugin`
                }
                gradlePlugin {
                    plugins {
                        create("gradleDevelocityConventions") {
                            id = "dv-conventions"
                            implementationClass = "my.DvConventionsPlugin"
                        }
                    }
                }
                $dvConventionsConfig
            """
        }
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'jar', '--scan', '-Dscan.dump'

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            // TODO:configuration-cache check problem details
            withTotalProblemsCount(1)
        }
        postBuildOutputContains 'Build scan written to'
    }

    @Issue("https://github.com/gradle/gradle/issues/19047")
    def "precompiled script plugin can use lambda expression with develocity plugin"() {
        given:
        createDir('dv-conventions') {
            file('src/main/kotlin/dv-conventions.settings.gradle.kts') << '''
                plugins {
                    com.gradle.develocity
                }

                develocity {
                    buildScan.obfuscation.hostname {
                        // lambda expression
                        "unset"
                    }
                }
            '''
            file('build.gradle.kts') << """
                plugins { `kotlin-dsl` }
                $dvConventionsConfig
            """
        }
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun 'jar', '--scan', '-Dscan.dump'

        then:
        configurationCache.assertStateStored()
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(0)
        }
        postBuildOutputContains 'Build scan written to'

        when:
        configurationCacheRun 'jar', '--scan', '-Dscan.dump'

        then:
        configurationCache.assertStateLoaded()
        postBuildOutputContains 'Build scan written to'
    }

    static String getDvConventionsConfig() {
        """
            dependencies {
                implementation("com.gradle:develocity-gradle-plugin:${AutoAppliedDevelocityPlugin.VERSION}")
            }
            ${KotlinDslTestUtil.kotlinDslBuildSrcConfig}
            repositories.gradlePluginPortal()
        """
    }
}
