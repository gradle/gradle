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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class OptionaFeaturesLocalComponentIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()
    }

    @Unroll("can consume an optional feature of a local component (#id)")
    def "can consume an optional feature of a local component"() {
        def mod = mavenHttpRepo.module("org", "foo", "1.0").publish()

        given:
        settingsFile << """
            include 'lib'
        """
        def libBuildFile = file("lib/build.gradle")
        javaLibrary()
        declareRepository()
        javaLibrary(libBuildFile)
        declareRepository(libBuildFile)

        buildFile << """            
            dependencies {
                api(project(":lib")) {
                    $consumerConfiguration
                }
            }
        """

        libBuildFile << """
            dependencies {
                api("org:foo:1.0") {
                    $producerConfiguration
                }
            }
        """

        when:
        if (resolves) {
            mod.pom.expectGet()
            mod.artifact.expectGet()
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":lib", "test:lib:unspecified") {
                    variant("apiElements", ["org.gradle.usage": "java-api"])
                    if (resolves) {
                        module("org:foo:1.0")
                    }
                    artifact(name: 'main', type: 'dir') // classes dir
                }
            }
        }

        where:
        id                                       | consumerConfiguration             | producerConfiguration                                            | resolves
        'all dependencies are mandatory'         | ''                                | ''                                                               | true
        'consumer ignores optional feature'      | ''                                | 'usedByOptionalFeature("feat")'                                  | false
        'consumer gets optional feature'         | 'includeOptionalFeature("feat")'  | 'usedByOptionalFeature("feat")'                                  | true
        'consumer asks for other feature'        | 'includeOptionalFeature("feat2")' | 'usedByOptionalFeature("feat")'                                  | false
        'producer dependency is not optional'    | 'includeOptionalFeature("feat")'  | ''                                                               | true
        'optional used in more than one feature' | 'includeOptionalFeature("feat")'  | 'usedByOptionalFeature("feat"); usedByOptionalFeature("feat2");' | true
    }

    private void declareRepository(TestFile file = buildFile) {
        file << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }
        """
    }

    private void javaLibrary(TestFile file = buildFile) {
        file << """
            apply plugin: 'java-library' 
        """
    }
}
