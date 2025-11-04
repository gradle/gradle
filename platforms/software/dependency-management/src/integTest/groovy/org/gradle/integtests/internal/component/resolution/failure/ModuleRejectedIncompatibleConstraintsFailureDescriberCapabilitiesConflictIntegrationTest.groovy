/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.internal.component.resolution.failure

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.dsl.GradleDsl

/**
 * This test is to ensure that the {@code ModuleRejectedIncompatibleConstraintsFailureDescriber}
 * does <strong>not</strong> incorrectly trigger upon <strong>capabilities</strong> conflicts between
 * different SLF4J logger implementations.
 * <p>
 * These are not actually incompatible constraints, and shouldn't be reported as such.
 */
class ModuleRejectedIncompatibleConstraintsFailureDescriberCapabilitiesConflictIntegrationTest extends AbstractIntegrationSpec {
    def resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")

    def "capability conflict with #first and #second added by plugin with multiple constraints involved does not trigger constraint conflict describer"() {
        given:
        buildKotlinFile << """
            plugins {
                `java-library`
                id("org.gradlex.jvm-dependency-conflict-resolution") version ("2.4")
            }

            repositories {
                ${mavenCentralRepository(GradleDsl.KOTLIN)}
            }

            dependencies {
                implementation(\"$first\")
                implementation(\"$second\")
            }

            tasks.register("resolve") {
                val consumerFiles = configurations.named("compileClasspath").get().files
                doLast {
                    consumerFiles.forEach {
                        println(it.name)
                    }
                }
            }
        """

        when:
        fails('resolve')

        then:
        result.getError().contains("Cannot select module with conflict on capability 'org.gradlex:slf4j-impl:1.0' also provided by")
        !result.getError().contains("Component is the target of multiple version constraints with conflicting requirements:")

        where:
        first                           | second
        'org.slf4j:slf4j-simple:1.7.27' | 'ch.qos.logback:logback-classic:1.2.3'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-log4j12:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-nop:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-jcl:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.slf4j:slf4j-jdk14:1.7.27'
        'org.slf4j:slf4j-simple:1.7.27' | 'org.apache.logging.log4j:log4j-slf4j-impl:2.17.0'
    }

    def "capability conflict on transitive dep with multiple constraints involved does not trigger constraint conflict describer"() {
        mavenRepo.module("org.hamcrest", "hamcrest-core", "2.2")
            .dependsOn(mavenRepo.module("org.hamcrest", "hamcrest", "2.2").publish())
            .publish()

        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenTestRepository()}

            dependencies {
                implementation("org.hamcrest:hamcrest-core")

                constraints {
                    implementation("org.hamcrest:hamcrest-core:2.2")
                }

                constraints {
                    implementation("org.hamcrest:hamcrest-core") {
                        version {
                            strictly("2.2")
                        }
                    }
                }
            }

            dependencies.components.withModule('org.hamcrest:hamcrest-core') {
                allVariants {
                    withCapabilities {
                        addCapability('org.hamcrest', 'hamcrest', id.version)
                    }
                }
            }

            tasks.register("resolve") {
                def consumerFiles = configurations.named("compileClasspath").get().files
                doLast {
                    consumerFiles.forEach {
                        println(it.name)
                    }
                }
            }
        """

        when:
        resolve.prepare()
        fails(":resolve")

        then:
        result.getError().contains("Cannot select module with conflict on capability 'org.hamcrest:hamcrest:2.2' also provided by")
        !result.getError().contains("Component is the target of multiple version constraints with conflicting requirements:")
    }
}
