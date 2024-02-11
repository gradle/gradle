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
import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import spock.lang.Issue

class ConfigurationCacheEnterprisePluginIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def setup() {
        settingsFile '''
            pluginManagement {
                includeBuild("ge-conventions")
            }
            plugins {
                id("ge-conventions")
            }
        '''

        buildFile '''
            plugins { id("java") }
        '''
    }

    @NotYetImplemented
    @Issue("https://github.com/gradle/gradle/issues/19047")
    def "problem is reported for Kotlin lambda expression with enterprise plugin"() {
        given:
        createDir('ge-conventions') {
            file('src/main/kotlin/my/GeConventionsPlugin.kt') << """
                package my

                import org.gradle.api.*
                import org.gradle.kotlin.dsl.*

                class GeConventionsPlugin : Plugin<$Settings.name> {
                    override fun apply(settings: $Settings.name) {
                        settings.apply(mapOf("plugin" to "com.gradle.enterprise"))
                        settings.gradleEnterprise {
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
                        create("gradleEnterpriseConventions") {
                            id = "ge-conventions"
                            implementationClass = "my.GeConventionsPlugin"
                        }
                    }
                }
                $geConventionsConfig
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
    def "precompiled script plugin can use lambda expression with enterprise plugin"() {
        given:
        createDir('ge-conventions') {
            file('src/main/kotlin/ge-conventions.settings.gradle.kts') << '''
                plugins {
                    com.gradle.enterprise
                }

                gradleEnterprise {
                    buildScan.obfuscation.hostname {
                        // lambda expression
                        "unset"
                    }
                }
            '''
            file('build.gradle.kts') << """
                plugins { `kotlin-dsl` }
                $geConventionsConfig
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

    static String getGeConventionsConfig() {
        """
            dependencies {
                implementation("com.gradle:gradle-enterprise-gradle-plugin:${AutoAppliedGradleEnterprisePlugin.VERSION}")
            }
            ${KotlinDslTestUtil.kotlinDslBuildSrcConfig}
            repositories.gradlePluginPortal()
        """
    }
}
