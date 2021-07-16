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

package org.gradle.api

import groovy.test.NotYetImplemented
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.operations.trace.BuildOperationRecord

class ParallelConfigurationFailureIntegrationTest extends AbstractIntegrationSpec {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    @NotYetImplemented
    def "does not configure projects in parallel"() {
        file("resolving/build.gradle") << """
            plugins {
                id 'base'
            }
            
            configurations {
                resolve
            }
            
            dependencies {
                (1..10).each { 
                    resolve project(":sub" + it) 
                } 
            }
            
            task resolveIt {
                inputs.files configurations.resolve
                doLast {
                    println configurations.resolve.files
                }
            }
            // This triggers dependency resolution and project evaluation
            configurations.resolve.resolvedConfiguration
        """
        (1..10).each {
            file("sub$it/build.gradle") << """
                plugins {
                    id 'java'
                }
            """
        }
        settingsFile << """
            rootProject.name = "test"
            (1..10).each {
                include "sub" + it
            }
            include "resolving"
        """

        when:
        succeeds("resolveIt")
        then:
        def resolving = operations.only("Configure project :resolving")
        def subprojects = operations.all(ConfigureProjectBuildOperationType) {
            it.displayName.contains("sub")
        }
        assertConfigurationIsSerial(resolving, subprojects)
    }

    private static void assertConfigurationIsSerial(BuildOperationRecord resolving, List<BuildOperationRecord> subprojects) {
        def startTime = resolving.startTime
        def endTime = resolving.endTime
        assert !subprojects.empty
        subprojects.each {
            assert !(startTime < it.startTime && it.endTime < endTime)
        }
    }
}
