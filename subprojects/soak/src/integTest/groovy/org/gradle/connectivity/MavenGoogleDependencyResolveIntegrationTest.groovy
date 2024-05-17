/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.connectivity

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@Requires(UnitTestPreconditions.Online)
class MavenGoogleDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        buildFile << """
            repositories {
                google()
                google { // just test this syntax works.
                    name = "otherGoogle"
                    content {
                        includeGroup 'org.sample'
                    }
                }
            }
        """
    }

    def "can query for human-readable repository name"() {
        given:
        buildFile << """
            task checkRepoName {
                doLast {
                    assert repositories*.name == ['Google', 'otherGoogle']
                }
            }
        """

        expect:
        succeeds 'checkRepoName'
    }

    def "can resolve AAR dependency and its transitive dependencies from repository"() {
        given:
        def dependencyGav = 'com.android.support.constraint:constraint-layout:1.0.2'
        buildFile << """
            configurations {
                compile
            }

            dependencies {
                compile '$dependencyGav'
            }

            task copyLibs(type: Copy) {
                from configurations.compile
                into "\$buildDir/libs"
            }
        """

        when:
        succeeds 'copyLibs'

        then:
        file('build/libs').assertHasDescendants('constraint-layout-1.0.2.aar', 'constraint-layout-solver-1.0.2.jar')
    }
}
