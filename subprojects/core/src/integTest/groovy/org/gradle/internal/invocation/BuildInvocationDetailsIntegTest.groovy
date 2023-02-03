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

package org.gradle.internal.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.buildevents.BuildStartedTime

class BuildInvocationDetailsIntegTest extends AbstractIntegrationSpec {

    def "can access build started time"() {
        when:
        buildFile << """
            tasks.register("timeTask", TimeConsumer)
            
            class TimeConsumer extends DefaultTask {
                private invocationDetails

                @javax.inject.Inject
                TimeConsumer(BuildInvocationDetails invocationDetails) {
                    this.invocationDetails = invocationDetails
                }
                
                @TaskAction
                def checkTime() {
                    def internalTimer = services.get($BuildStartedTime.name)
                    assert invocationDetails.buildStartedTime != 0
                    assert invocationDetails.buildStartedTime == internalTimer.startTime
                    
                    println "startTime: " + invocationDetails.buildStartedTime
                }
            }
                    
                    
        """

        succeeds("timeTask")

        then:
        output.contains("startTime: ")
    }
}
