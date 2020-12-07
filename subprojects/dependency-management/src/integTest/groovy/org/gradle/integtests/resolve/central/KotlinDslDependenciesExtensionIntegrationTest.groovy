/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve.central

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

/**
 * This test isn't meant to check the behavior of the extension generation like the other
 * integration tests in this package, but only what is very specific to the Kotlin DSL.
 * Because it requires the generated Gradle API it runs significantly slower than the other
 * tests so avoid adding tests here if they cannot be expressed with the Groovy DSL.
 */
class KotlinDslDependenciesExtensionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsKotlinFile << """
            rootProject.name = "test"

            dependencyResolutionManagement {
                repositories {
                    maven {
                        setUrl("${mavenHttpRepo.uri}")
                    }
                }
            }
        """
    }

    @UnsupportedWithConfigurationCache(because = "test uses project state directly")
    def "can override version of a library via an extension method"() {
        def lib = mavenHttpRepo.module('org.gradle.test', 'lib', '1.1').publish()
        settingsKotlinFile << """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        alias("my-lib").to("org.gradle.test:lib:1.0")
                    }
                }
            }
        """
        buildKotlinFile << """
            plugins {
                `java-library`
            }

            dependencies {
                implementation(libs.my.lib) {
                    version {
                        strictly("1.1")
                    }
                }
            }

            tasks.register("checkDeps") {
                inputs.files(configurations.compileClasspath)
                doLast {
                    val fileNames = configurations.compileClasspath.files.map(File::name)
                    assert(fileNames == listOf("lib-1.1.jar"))
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        succeeds ':checkDeps'
    }
}
