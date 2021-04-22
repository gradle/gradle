/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl


class VersionNumberIntegrationTest extends AbstractIntegrationSpec {

    def "nullability with Kotlin jsr-305 strict"() {

        given:
        file("src/main/kotlin/Test.kt") << """
            import org.gradle.util.VersionNumber

            fun test() {
                val currentAgpVersion = VersionNumber.parse("1.0")
                require(null != currentAgpVersion)
                require(currentAgpVersion != null)
            }
        """
        buildKotlinFile << """
            import org.gradle.util.VersionNumber
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                kotlin("jvm") version embeddedKotlinVersion
            }

            ${jcenterRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation(gradleApi())
                implementation(kotlin("stdlib"))
            }

            tasks.withType<KotlinCompile>().configureEach {
                kotlinOptions {
                    freeCompilerArgs = listOf("-Xjsr305=strict")
                }
            }

            // Also assert Gradle Kotlin DSL script compilation works
            val currentAgpVersion: VersionNumber? = VersionNumber.parse("1.0")
            require(null != currentAgpVersion)
            require(currentAgpVersion != null)
        """

        executer.expectDocumentedDeprecationWarning("Configuration 'api' extends deprecated configuration 'compile'. This will fail or cause unintended side effects in future Gradle versions. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
        executer.expectDocumentedDeprecationWarning("Configuration 'testApi' extends deprecated configuration 'testCompile'. This will fail or cause unintended side effects in future Gradle versions. " +
            "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_5.html#dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
        expect:
        succeeds 'classes'
    }
}
