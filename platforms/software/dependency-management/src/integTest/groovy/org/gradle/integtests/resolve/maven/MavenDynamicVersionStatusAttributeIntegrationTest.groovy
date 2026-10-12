/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/8126")
class MavenDynamicVersionStatusAttributeIntegrationTest extends AbstractDependencyResolutionTest {

    private static final String STATUS_ATTRIBUTE = "Attribute.of(\"${ProjectInternal.STATUS_ATTRIBUTE.name}\", String)"

    private static String statusOf(String version) {
        version.endsWith('-SNAPSHOT') ? 'integration' : 'release'
    }

    private static String rejectionReason(String version, String requestedStatus) {
        "rejection: version $version:   - Attribute '${ProjectInternal.STATUS_ATTRIBUTE.name}' didn't match. Requested '$requestedStatus', was: '${statusOf(version)}'"
    }

    def resolve = new ResolveTestFixture(testDirectory)

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            plugins {
                id("jvm-ecosystem")
            }

            repositories {
                maven {
                    url = "${mavenRepo.uri}"
                }
            }

            configurations {
                compile
            }

            ${resolve.configureProject("compile")}
        """

        publishModule('releaseHead')
    }

    private void publishModule(String name) {
        mavenRepo.module('com.example', name, '1.0').publish()
        mavenRepo.module('com.example', name, '1.1').publish()
        mavenRepo.module('com.example', name, '1.2-SNAPSHOT').withNonUniqueSnapshots().publish()
        mavenRepo.module('com.example', name, '1.3').publish()
        mavenRepo.module('com.example', name, '2.0').publish()
    }

    def "dynamic version #selector selects #selected regardless of status when none is requested"() {
        given:
        buildFile << """
            dependencies {
                compile("com.example:releaseHead:$selector")
            }
        """

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.example:releaseHead:$selector", "com.example:releaseHead:$selected") {
                    notRequested()
                    byReason(rangeReason)
                }
            }
        }

        where:
        selector   | selected       | rangeReason
        '[1,2)'    | '1.3'          | "didn't match version 2.0"
        '1.+'      | '1.3'          | "didn't match version 2.0"
        '[1,1.3)'  | '1.2-SNAPSHOT' | "didn't match versions 2.0, 1.3"
    }

    def "#status status requested on a dependency skips the higher #rejected without affecting the other dependencies"() {
        given:
        publishModule('other')
        buildFile << """
            dependencies {
                compile("com.example:releaseHead:$selector") {
                    attributes {
                        attribute($STATUS_ATTRIBUTE, "$status")
                    }
                }
                compile("com.example:other:[1,2)")
            }
        """
        def rejection = rejectionReason(rejected, status)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.example:releaseHead:$selector", "com.example:releaseHead:$selected") {
                    notRequested()
                    byReason(rangeReason)
                    byReason(rejection)
                }
                edge("com.example:other:[1,2)", "com.example:other:1.3") {
                    notRequested()
                    byReason("didn't match version 2.0")
                }
            }
        }

        where:
        selector  | status        | selected       | rejected       | rangeReason
        '[1,1.3)' | 'release'     | '1.1'          | '1.2-SNAPSHOT' | "didn't match versions 2.0, 1.3"
        '[1,2)'   | 'integration' | '1.2-SNAPSHOT' | '1.3'          | "didn't match version 2.0"
    }

    def "#status status requested on a dependency with a prefix version skips the higher #rejected"() {
        given:
        publishModule('snapshotHead')
        mavenRepo.module('com.example', 'snapshotHead', '1.4-SNAPSHOT').withNonUniqueSnapshots().publish()
        buildFile << """
            dependencies {
                compile("com.example:$module:1.+") {
                    attributes {
                        attribute($STATUS_ATTRIBUTE, "$status")
                    }
                }
            }
        """
        def rejection = rejectionReason(rejected, status)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.example:$module:1.+", "com.example:$module:$selected") {
                    notRequested()
                    byReason("didn't match version 2.0")
                    byReason(rejection)
                }
            }
        }

        where:
        module          | status        | selected       | rejected
        'snapshotHead'  | 'release'     | '1.3'          | '1.4-SNAPSHOT'
        'releaseHead'   | 'integration' | '1.2-SNAPSHOT' | '1.3'
    }

    def "#status status requested on a configuration skips the higher #rejected for every dependency"() {
        given:
        publishModule('other')
        buildFile << """
            configurations.compile.attributes.attribute($STATUS_ATTRIBUTE, "$status")

            dependencies {
                compile("com.example:releaseHead:$selector")
                compile("com.example:other:$selector")
            }
        """
        def rejection = rejectionReason(rejected, status)

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.example:releaseHead:$selector", "com.example:releaseHead:$selected") {
                    notRequested()
                    byReason(rangeReason)
                    byReason(rejection)
                }
                edge("com.example:other:$selector", "com.example:other:$selected") {
                    notRequested()
                    byReason(rangeReason)
                    byReason(rejection)
                }
            }
        }

        where:
        selector  | status        | selected       | rejected       | rangeReason
        '[1,1.3)' | 'release'     | '1.1'          | '1.2-SNAPSHOT' | "didn't match versions 2.0, 1.3"
        '[1,2)'   | 'integration' | '1.2-SNAPSHOT' | '1.3'          | "didn't match version 2.0"
    }

    def "status requested on an extended configuration is not inherited by the resolving configuration"() {
        given:
        buildFile << """
            configurations {
                compileDeps
                compile.extendsFrom(compileDeps)
            }

            configurations.compileDeps.attributes.attribute($STATUS_ATTRIBUTE, "release")

            dependencies {
                compileDeps("com.example:releaseHead:[1,2)")
            }
        """

        when:
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.example:releaseHead:[1,2)", "com.example:releaseHead:1.3") {
                    notRequested()
                    byReason("didn't match version 2.0")
                }
            }
        }
    }

    def "requesting #status status on the configuration fails for a dependency on a static #version version"() {
        given:
        buildFile << """
            configurations.compile.attributes.attribute($STATUS_ATTRIBUTE, "$status")

            dependencies {
                compile("com.example:releaseHead:$version")
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("No matching variant of com.example:releaseHead:$version was found. The consumer was configured to find a component, with a $status status but:")
        failure.assertThatCause(containsNormalizedString("Incompatible because this component declares a component, with a ${statusOf(version)} status and the consumer needed a component, with a $status status"))

        where:
        status        | version
        'release'     | '1.2-SNAPSHOT'
        'integration' | '1.1'
    }
}
