/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl


import spock.lang.Issue

class ConfigurationCacheKotlinDslIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    @Issue("https://github.com/gradle/gradle/issues/30145")
    def "compilation of kotlin dsl build script does not cause cache misses when all system properties are input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildKotlinFile """
            System.getProperties().forEach { _, _ -> }  // Mark all properties as an input

            tasks.register("run") {
                doLast {
                    println("idea.io.use.nio2=\${System.getProperty("idea.io.use.nio2")}")
                }
            }
        """

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateLoaded()
    }

    @Issue("https://github.com/gradle/gradle/issues/30145")
    def "compilation of kotlin dsl plugin does not cause cache misses when all system properties are input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        // We have to use Groovy scripts here to reproduce the original issue. Precompiled script compilation should be the first Kotlin compilation job done by Gradle.
        settingsFile """
            includeBuild("plugin")
        """

        buildFile("plugin/build.gradle", """
            plugins {
                id "org.gradle.kotlin.kotlin-dsl" version "5.1.0"  // TODO(mlopatkin): this should be the "current" Kotlin DSL version but it isn't available for Groovy scripts.
            }

            repositories {
                gradlePluginPortal()
            }
        """)

        file("plugin/src/main/kotlin/my.plugin.gradle.kts") << """
            tasks.register("run") {
                doLast {
                    println("idea.io.use.nio2=\${System.getProperty("idea.io.use.nio2")}")
                }
            }
        """

        buildFile """
            plugins {
                id("my.plugin")
            }
            System.getProperties().forEach { k, v -> }  // Mark all properties as an input
        """

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("run")

        then:
        configurationCache.assertStateLoaded()
    }
}
