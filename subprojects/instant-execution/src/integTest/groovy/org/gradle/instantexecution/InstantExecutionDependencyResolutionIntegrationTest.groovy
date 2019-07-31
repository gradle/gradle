/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.integtests.resolve.transform.ArtifactTransformTestFixture

class InstantExecutionDependencyResolutionIntegrationTest extends AbstractInstantExecutionIntegrationTest implements ArtifactTransformTestFixture {
    def setup() {
        // So that dependency resolution results from previous executions do not interfere
        requireOwnGradleUserHomeDir()
    }

    def "task input files can include the output of artifact transform of project dependencies"() {
        settingsFile << """
            include 'a', 'b'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                green {
                    attributes.attribute(color, 'green')
                }
            }
            dependencies {
                implementation project(':a')
                implementation files('root.green')
                implementation project(':b')
            }
        """
        file('root.green') << 'root'

        expect:
        instantRun(":resolve")
        outputContains("result = [root.green, a.jar.green, b.jar.green]")
        instantRun(":resolve")
        // For now, scheduled transforms are ignored when writing to the cache
        outputContains("result = [root.green]")
    }

    def "task input files can include the output of artifact transform of external dependencies"() {
        withColorVariants(mavenRepo.module("group", "thing1", "1.2")).publish()
        withColorVariants(mavenRepo.module("group", "thing2", "1.2")).publish()

        setupBuildWithSimpleColorTransform()
        buildFile << """
            repositories {
                maven { 
                    url = uri('${mavenRepo.uri}') 
                    metadataSources { gradleMetadata() }
                }
            } 
            dependencies {
                implementation "group:thing1:1.2"
                implementation "group:thing2:1.2"
            }
        """

        expect:
        instantRun(":resolve")
        outputContains("result = [thing1-1.2.jar.green, thing2-1.2.jar.green]")
        instantRun(":resolve")
        // For now, scheduled transforms are ignored when writing to the cache
        outputContains("result = []")
    }

    def "task input files can include the output of artifact transforms of prebuilt file dependencies"() {
        settingsFile << """
            include 'a'
        """
        setupBuildWithSimpleColorTransform()
        buildFile << """
            dependencies.artifactTypes {
                blue {
                    attributes.attribute(color, 'blue')
                }
            }
            dependencies {
                implementation files('root.blue')
                implementation project(':a')
            }
            project(':a') {
                dependencies {
                    implementation files('a.blue')
                }
            }
        """
        file('root.blue') << 'root'
        file('a/a.blue') << 'a'

        expect:
        instantRun(":resolve")
        outputContains("result = [root.blue.green, a.jar.green, a.blue.green]")
        instantRun(":resolve")
        // For now, scheduled transforms are ignored when writing to the cache
        outputContains("result = [root.blue.green, a.blue.green]")
    }
}
