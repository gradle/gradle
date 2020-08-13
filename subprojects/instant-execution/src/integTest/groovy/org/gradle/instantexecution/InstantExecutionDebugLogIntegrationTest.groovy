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

import org.gradle.api.DefaultTask

import static org.gradle.instantexecution.fingerprint.InstantExecutionCacheFingerprint.GradleEnvironment

class InstantExecutionDebugLogIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "logs categorized open/close frame events for state and fingerprint files"() {
        given:
        buildFile << """
            task ok { doLast { println('ok!') } }
        """

        when:
        withDebugLogging()
        instantRun 'ok'

        then: "fingerprint frame events are logged"
        outputContains '[configuration cache fingerprint] {"type":"O","frame":"' + GradleEnvironment.name + '","at":'
        outputContains '[configuration cache fingerprint] {"type":"C","frame":"' + GradleEnvironment.name + '","at":'

        and: "state frame events are logged"
        outputContains '[configuration cache state] {"type":"O","frame":":ok","at":'
        outputContains '[configuration cache state] {"type":"O","frame":"' + DefaultTask.name + '","at":'
        outputContains '[configuration cache state] {"type":"C","frame":"' + DefaultTask.name + '","at":'
        outputContains '[configuration cache state] {"type":"C","frame":":ok","at":'
    }
}
