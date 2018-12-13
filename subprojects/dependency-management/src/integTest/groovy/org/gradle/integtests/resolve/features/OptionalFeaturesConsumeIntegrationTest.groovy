/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.features

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Ignore
import spock.lang.Unroll

@RequiredFeatures(
        @RequiredFeature(feature= GradleMetadataResolveRunner.REPOSITORY_TYPE, value="maven")
)
class OptionalFeaturesConsumeIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Ignore
    def "workaround for Spock Unroll bug"() {
        expect:
        true
    }

    @Unroll("can get optional dependencies from a Maven module (ask for optional = #askForOptional)")
    def "can get optional dependencies from a Maven module"() {
        repository {
            "org:foo:1.0" {
                variant('api') {
                    dependsOn("org:bar:1.0") {
                        usedByOptionalFeature("feat")
                    }
                }
                variant('runtime') {
                    dependsOn("org:bar:1.0") {
                        usedByOptionalFeature("feat")
                    }
                }
            }
            "org:bar:1.0"()
        }

        given:
        buildFile << """
            dependencies {
                conf("org:foo:1.0") {
                    if ($askForOptional) includeOptionalFeature("feat")
                }
            }
        """
        repositoryInteractions {
            "org:foo:1.0" {
                expectResolve()
            }
            "org:bar:1.0" {
                if (askForOptional) {
                    expectResolve()
                }
            }
        }

        when:
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:foo:1.0') {
                    if (askForOptional) {
                        module('org:bar:1.0')
                    }
                }
            }
        }

        where:
        askForOptional << [true, false]
    }
}
