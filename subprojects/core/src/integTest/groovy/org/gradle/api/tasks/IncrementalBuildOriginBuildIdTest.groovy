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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IncrementalBuildOriginBuildIdTest extends AbstractIntegrationSpec {

    def "exposes origin build id"() {
        given:
        buildScript """
            def write = tasks.create("write", WriteProperties) {
                outputFile = "out.properties"
                properties = [v: 1]
            }
            
            println "buildId: \${gradle.buildIds.buildId}"
            
            gradle.taskGraph.afterTask {
                if (it == write) {
                    println "originBuildId: \${it.state.originBuildId}"
                }
            }
        """

        when:
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def firstBuildId = currentBuildId()
        originBuildId() == null

        when:
        succeeds "write"

        then:
        executed ":write"
        def secondBuildId = currentBuildId()
        firstBuildId != secondBuildId
        originBuildId() == firstBuildId

        when:
        buildFile << """
            write.property("changed", "now")
        """
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        def thirdBuildId = currentBuildId()
        firstBuildId != thirdBuildId
        secondBuildId != thirdBuildId
        originBuildId() == null

        when:
        succeeds "write"

        then:
        executed ":write"
        originBuildId() == thirdBuildId
    }

    String currentBuildId() {
        (output =~ /buildId: ([\w]{26})/)[0][1]
    }

    String originBuildId() {
        def value = (output =~ /originBuildId: (null|[\w]{26})/)[0][1]
        value == "null" ? null : value
    }
}
