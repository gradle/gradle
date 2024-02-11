/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

@Requires(IntegTestPreconditions.NotParallelExecutor)
class ArtifactTransformWithDependenciesParallelIntegrationTest extends AbstractHttpDependencyResolutionTest implements ArtifactTransformTestFixture {
    @Issue("https://github.com/gradle/gradle/issues/20975")
    def "transform is applied to project output when project and external library have conflicting group and module name"() {
        mavenRepo.module("libs", "producer", "1.0").publish()

        createDirs("producer", "consumer")
        settingsFile """
            include 'producer', 'consumer'
        """

        setupBuildWithColorTransformThatTakesUpstreamArtifacts()
        buildFile """
            allprojects {
                repositories {
                    maven { url = "$mavenRepo.uri" }
                }
            }
            dependencies {
                implementation project(':consumer')
                implementation project(':producer')
            }
            project(':producer') {
                group = "libs"
                version = "2.0"
                // Introduce a task to delay the production of the jar
                task slow {
                    doLast {
                        // Block production of the jar but allow other nodes to run
                        services.get(${ProjectLeaseRegistry.name}.class).runAsIsolatedTask()
                        sleep(500)
                    }
                }
                tasks.producer.dependsOn(slow)
            }
            project(':consumer') {
                dependencies {
                    // An external dependency that will be substituted with a dependency on project 'producer'
                    implementation "libs:producer:1.0"
                }
            }
        """

        when:
        succeeds(":resolve", "--parallel")

        then:
        outputContains("processing producer.jar using []")
        outputContains("processing consumer.jar using [producer.jar]")

        when:
        succeeds(":resolve", "--parallel")

        then:
        outputDoesNotContain("processing")

        when:
        succeeds(":resolve", "--parallel", "-DproducerContent=changed")

        then:
        outputContains("processing producer.jar using []")
        outputContains("processing consumer.jar using [producer.jar]")
    }
}
