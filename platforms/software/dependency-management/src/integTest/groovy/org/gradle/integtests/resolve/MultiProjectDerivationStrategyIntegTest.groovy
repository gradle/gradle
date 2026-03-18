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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractPolyglotIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class MultiProjectDerivationStrategyIntegTest extends AbstractPolyglotIntegrationSpec {

    def "different projects can use different variant derivation strategies without leaking to each other"() {
        mavenRepo.module("org", "foo", '1.1').publish()

        def resolve = new ResolveTestFixture(testDirectory)
        def resolveScript = testDirectory.file("resolve.gradle")
        resolveScript << resolve.configureProject("conf")

        writeSpec {
            settings {
                rootProjectName = 'test'
            }
            project("a") {
                group = 'org'
                version = '1.0'
                repositories {
                    maven(mavenRepo.uri)
                }
                configurations {
                    conf
                }
                dependencies {
                    conf('org:foo:1.1')
                }
                applyFrom(resolveScript)
            }
            project("b") {
                plugin("jvm-ecosystem")
                group = 'org'
                version = '1.0'
                repositories {
                    maven(mavenRepo.uri)
                }
                configurations {
                    conf
                }
                dependencies {
                    conf('org:foo:1.1')
                }
                applyFrom(resolveScript)
            }
        }

        when:
        run 'a:checkDeps'

        then:
        resolve.expectGraph(":a") {
            root(":a", "org:a:1.0") {
                module("org:foo:1.1") {
                    variant "default", ['org.gradle.status': 'release']
                }
            }
        }

        when:
        run ':b:checkDeps'

        then:
        resolve.expectGraph(":b") {
            root(":b", "org:b:1.0") {
                module("org:foo:1.1") {
                    variant "runtime", [
                        'org.gradle.status': 'release',
                        'org.gradle.category':'library',
                        'org.gradle.libraryelements':'jar',
                        'org.gradle.usage':'java-runtime'
                    ]
                }
            }
        }
    }
}
