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

package org.gradle.api.plugins.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.component.resolution.failure.exception.VariantSelectionException
import org.gradle.test.fixtures.dsl.GradleDsl

class KMPFailureDescribersPluginIntegrationTest extends AbstractIntegrationSpec {
    def "demonstrate failure describer for KMP dependency missing required target"() {
        given: "a consumer KMP JVM project that depends on another producer KMP library"
        settingsKotlinFile << """
            pluginManagement {
                plugins {
                    kotlin("multiplatform") version "${new KotlinGradlePluginVersions().getLatest()}"
                }

                ${mavenCentralRepository(GradleDsl.KOTLIN)}
            }

            include("producer")
        """

        buildKotlinFile << """
            plugins {
                kotlin("multiplatform")
            }

            apply<org.gradle.api.plugins.internal.KMPFailureDescribersPlugin>()

            kotlin {
                jvm()

                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(project(":producer"))
                        }
                    }
                }
            }
        """

        and: "...where the producer is a KMP project NOT targeting JVM"
        file("producer/build.gradle.kts") << """
            plugins {
                kotlin("multiplatform")
            }

            kotlin {
                js(IR) {
                    browser()
                }
            }
        """

        when:
        fails 'build', "--stacktrace"

        then:
        failure.assertHasErrorOutput("""> Could not resolve all task dependencies for configuration ':jvmCompileClasspath'.
   > Could not resolve project :producer.
     Required by:
         project :
      > Kotlin Multiplatform project requested a jvm target, but project :producer doesn't provide jvm support.""")
        failure.assertHasErrorOutput("Caused by: " + VariantSelectionException.class.getName())
        failure.assertHasResolution("Update the dependency on 'project :producer' to a different version that supports the jvm platform.")
        failure.assertHasResolution("Remove support for the jvm platform from this project.")
    }
}
