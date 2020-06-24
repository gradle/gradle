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

package org.gradle.instantexecution

import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

import java.util.concurrent.TimeUnit

class InstantExecutionDependencyResolutionFeaturesIntegrationTest extends AbstractInstantExecutionIntegrationTest implements TasksWithInputsAndOutputs {
    @Rule
    HttpServer server = new HttpServer()
    def remoteRepo = new MavenHttpRepository(server, mavenRepo)

    @Override
    def setup() {
        // So that dependency resolution results from previous tests do not interfere
        executer.requireOwnGradleUserHomeDir()
    }

    def "invalidates configuration cache entry when dynamic version expiry is reached"() {
        given:
        server.start()

        remoteRepo.module("thing", "lib", "1.2").publish()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    def timeout = providers.gradleProperty("cache-for").forUseAtConfigurationTime().orElse(7).get().toInteger()
                    resolutionStrategy.cacheDynamicVersionsFor(timeout, ${TimeUnit.name}.DAYS)
                }
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def fixture = newInstantExecutionFixture()

        remoteRepo.getModuleMetaData("thing", "lib").expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve1")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when version list is cached
        instantRun("resolve2")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        instantRun("resolve2")

        then:
        fixture.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // use a shorter expiry
        remoteRepo.getModuleMetaData("thing", "lib").expectHead()

        instantRun("resolve1", "-Pcache-for=0")

        then:
        fixture.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        remoteRepo.getModuleMetaData("thing", "lib").expectHead()
        instantRun("resolve1", "-Pcache-for=0")

        then:
        fixture.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached version information for thing:lib:1.+ has expired.")
        outputContains("result = [lib-1.3.jar]")
    }
}
