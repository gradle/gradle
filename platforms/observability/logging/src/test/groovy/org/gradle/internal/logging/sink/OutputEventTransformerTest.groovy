/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.sink

import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Subject

@Subject(OutputEventTransformer)
class OutputEventTransformerTest extends OutputSpecification {
    def forwarded = []
    def listener = Stub(OutputEventListener) {
        onOutput(_) >> { OutputEvent event -> forwarded << event }
    }
    def transformer = new OutputEventTransformer(listener, new Object())

    def "keeps the owner of a start event that is given the parent of its discarded parent"() {
        when:
        transformer.onOutput(start(id: 1L, buildOperationStart: true, status: "Running build"))
        // Without status, logging header or category, so discarded
        transformer.onOutput(start(id: 2L, parentId: 1L, buildOperationStart: true))
        transformer.onOutput(start(id: 3L, parentId: 2L, buildOperationStart: true, status: "Evaluating settings", owner: ":lib"))

        then:
        forwarded*.progressOperationId == [new OperationIdentifier(1L), new OperationIdentifier(3L)]
        forwarded[1].parentProgressOperationId == new OperationIdentifier(1L)
        forwarded[1].status == "Evaluating settings"
        forwarded[1].owner == ":lib"
    }
}
