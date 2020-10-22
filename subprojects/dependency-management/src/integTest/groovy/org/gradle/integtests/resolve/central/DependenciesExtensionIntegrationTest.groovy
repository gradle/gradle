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

import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import spock.lang.Unroll

class DependenciesExtensionIntegrationTest  extends AbstractCentralDependenciesIntegrationTest {

    @Unroll
    @UnsupportedWithConfigurationCache(because="the test uses an extension directly in the task body")
    def "dependencies declared in settings trigger the creation of an extension (notation=#notation)"() {
        settingsFile << """
            dependencyResolutionManagement {
                dependenciesModel {
                    $notation
                }
            }
        """

        buildFile << """
            apply plugin: 'java-library'

            tasks.register("verifyExtension") {
                doLast {
                    def lib = libs.foo
                    assert lib instanceof Provider
                    def dep = lib.get()
                    assert dep instanceof MinimalExternalModuleDependency
                    assert dep.module.group == 'org.gradle.test'
                    assert dep.module.name == 'lib'
                    assert dep.versionConstraint.requiredVersion == '1.0'
                }
            }
        """

        when:
        run 'verifyExtension'

        then:
        operations.hasOperation("Generate dependency accessors")

        when: "no change in settings"
        run 'verifyExtension'

        then: "extension is not regenerated"
        !operations.hasOperation("Generate dependency accessors")

        when: "adding a library to the model"
        settingsFile << """
            dependencyResolutionManagement {
                dependenciesModel {
                    alias("bar", "org.gradle.test:bar:1.0")
                }
            }
        """
        run 'verifyExtension'
        then: "extension is regenerated"
        operations.hasOperation("Generate dependency accessors")

        when: "updating a version in the model"
        settingsFile.text = settingsFile.text.replace('org.gradle.test:bar:1.0', 'org.gradle.test:bar:1.1')
        run 'verifyExtension'

        then: "extension is not regenerated"
        !operations.hasOperation("Generate dependency accessors")

        where:
        notation << [
            'alias("foo", "org.gradle.test:lib:1.0")',
            'alias("foo", "org.gradle.test", "lib") { require "1.0" }'
        ]
    }

    def "can use the generated extension to declare a dependency"() {
        settingsFile << """
            dependencyResolutionManagement {
                dependenciesModel {
                    alias("myLib", "org.gradle.test", "lib") {
                        require "1.0"
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.0").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation libs.myLib
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.0')
            }
        }

    }

    def "can use the generated extension to declare a dependency and override the version"() {
        settingsFile << """
            dependencyResolutionManagement {
                dependenciesModel {
                    alias("myLib", "org.gradle.test", "lib") {
                        require "1.0"
                    }
                }
            }
        """
        def lib = mavenHttpRepo.module("org.gradle.test", "lib", "1.1").publish()
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(libs.myLib) {
                    version {
                        require '1.1'
                    }
                }
            }
        """

        when:
        lib.pom.expectGet()
        lib.artifact.expectGet()

        then:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.gradle.test:lib:1.1')
            }
        }

    }
}
