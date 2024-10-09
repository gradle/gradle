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

package org.gradle.integtests.resolve.typesafe

import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class TypeSafeRootProjectAccessorsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {
    def "generates type-safe accessors for a root project with name = #name)"() {
        given:
        settingsFile << """
            rootProject.name = '$name'
        """
        buildFile << """
            plugins {
                id("java-library")
            }
            sourceSets {
                foo
            }
            java {
                registerFeature("foo") {
                    usingSourceSet(sourceSets.foo)
                }
            }

            dependencies {
                implementation(projects.${accessor}) {
                    capabilities {
                        it.requireFeature("foo")
                    }
                }
            }
        """
        def resolve = new ResolveTestFixture(buildFile)
        resolve.prepare("runtimeClasspath")

        when:
        succeeds(":checkDeps")

        then:
        resolve.expectGraph {
            root(":", ":${name}:unspecified") {
                project(":", ":${name}:unspecified") {
                    artifact(name: "${name}-foo", fileName: "${name}-foo.jar")
                }
            }
        }

        where:
        name         | accessor
        'test'       | 'test'
        'root'       | 'root'
        'snake_case' | 'snakeCase'
        'kebab-case' | 'kebabCase'
        'camelCase'  | 'camelCase'

    }
}
