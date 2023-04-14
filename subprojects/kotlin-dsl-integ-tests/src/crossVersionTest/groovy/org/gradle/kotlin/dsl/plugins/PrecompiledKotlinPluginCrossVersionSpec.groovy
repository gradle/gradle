/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.util.GradleVersion

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN
import static org.junit.Assume.assumeTrue

@TargetVersions("5.0+")
class PrecompiledKotlinPluginCrossVersionSpec extends CrossVersionIntegrationSpec {

    def "precompiled Kotlin plugin built with Gradle 5.0+ can be used with current Gradle version"() {
        assumeTrue(previous.version >= GradleVersion.version('5.0'))

        given:
        precompiledKotlinPluginBuiltWith(version(previous))

        when:
        def result = pluginAppliedWith(version(current)).run()

        then:
        result.assertOutputContains("My plugin applied!")
        result.assertOutputContains("My task executed!")
    }

    def "precompiled Kotlin plugin built with current version can be used with Gradle 6.0+"() {
        assumeTrue(previous.version >= GradleVersion.version('6.0'))

        given:
        precompiledKotlinPluginBuiltWith(version(current))

        when:
        def result = pluginAppliedWith(version(previous)).run()

        then:
        result.assertOutputContains("My plugin applied!")
        result.assertOutputContains("My task executed!")
    }

    private void precompiledKotlinPluginBuiltWith(GradleExecuter executer) {
        file("plugin/settings.gradle.kts").text = ""
        file("plugin/build.gradle.kts").text = """
            plugins {
                `kotlin-dsl`
                `maven-publish`
            }
            group = "com.example"
            version = "1.0"
            ${mavenCentralRepository(KOTLIN)}
            publishing {
                repositories {
                    maven { url = uri("${mavenRepo.uri}") }
                }
            }
        """
        file("plugin/src/main/kotlin/my-plugin.gradle.kts").text = """
            tasks.register("myTask") {
                doLast {
                    println("My task executed!")
                }
            }
            println("My plugin applied!")
        """
        executer.inDirectory(file("plugin")).withTasks("publish").run()
    }

    private GradleExecuter pluginAppliedWith(GradleExecuter executer) {
        file("consumer/settings.gradle.kts").text = """
            pluginManagement {
                repositories {
                    maven(url = "${mavenRepo.uri}")
                }
            }
        """
        file("consumer/build.gradle.kts").text = """
            plugins {
                id("my-plugin") version "1.0"
            }
        """
        return executer.inDirectory(file("consumer")).withTasks("myTask")
    }
}
