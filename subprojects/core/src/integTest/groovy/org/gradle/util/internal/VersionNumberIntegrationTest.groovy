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

package org.gradle.util.internal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.junit.Assume.assumeFalse


class VersionNumberIntegrationTest extends AbstractIntegrationSpec {

    def "nullability with Kotlin jsr-305 strict"() {
        assumeFalse(GradleContextualExecuter.embedded)

        given:
        file("src/main/kotlin/Test.kt") << """
            import org.gradle.util.internal.VersionNumber

            fun test() {
                val currentAgpVersion = VersionNumber.parse("1.0")
                require(null != currentAgpVersion)
                require(currentAgpVersion != null)
            }
        """
        buildKotlinFile << """
            import org.gradle.util.internal.VersionNumber
            import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

            plugins {
                kotlin("jvm") version embeddedKotlinVersion
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}

            dependencies {
                implementation(gradleApi())
                implementation(kotlin("stdlib"))
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    freeCompilerArgs.add("-Xjsr305=strict")
                }
            }

            // Also assert Gradle Kotlin DSL script compilation works
            val currentAgpVersion: VersionNumber? = VersionNumber.parse("1.0")
            require(null != currentAgpVersion)
            require(currentAgpVersion != null)
        """

        expect:
        succeeds 'classes'
    }
}
