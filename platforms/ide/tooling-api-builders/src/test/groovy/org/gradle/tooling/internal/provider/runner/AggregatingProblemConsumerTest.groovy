/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.internal.build.event.types.DefaultDocumentationLink
import org.gradle.internal.build.event.types.DefaultProblemDefinition
import org.gradle.internal.build.event.types.DefaultProblemGroup
import org.gradle.internal.build.event.types.DefaultProblemId
import org.gradle.internal.build.event.types.DefaultSeverity
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.tooling.internal.protocol.InternalBasicProblemDetailsVersion3
import org.gradle.tooling.internal.protocol.InternalProblemEventVersion2
import spock.lang.Specification

class AggregatingProblemConsumerTest extends Specification {
    def "distinct events are not aggregated"() {
        given:
        def eventConsumer = Mock(ProgressEventConsumer)
        def emitter = new AggregatingProblemConsumer(eventConsumer, { new OperationIdentifier(1) })


        when:
        for (int i = 0; i < 3; i++) {
            emitter.emit(createMockProblem("foo$i"))
        }

        then:
        3 * eventConsumer.progress(_)
    }

    def "emit summary if there are deduplicate events"() {
        given:
        def eventConsumer = Mock(ProgressEventConsumer)
        def emitter = new AggregatingProblemConsumer(eventConsumer, { new OperationIdentifier(1) })

        when:
        for (int i = 0; i < 15; i++) {
            emitter.emit(createMockProblem("foo"))
        }

        emitter.sendProblemSummaries()

        then:
        2 * eventConsumer.progress(_ as InternalProblemEventVersion2)
    }

    def "single unique events are not aggregated"() {
        given:
        def eventConsumer = Mock(ProgressEventConsumer)
        def emitter = new AggregatingProblemConsumer(eventConsumer, { new OperationIdentifier(1) })

        when:
        emitter.emit(createMockProblem("foo"))

        emitter.sendProblemSummaries()

        then:
        1 * eventConsumer.progress(_ as InternalProblemEventVersion2)
    }

    static thresholdForIntermediateSummary = 20

    def "send intermediate event after similar events"() {
        given:
        def eventConsumer = Mock(ProgressEventConsumer)
        def emitter = new AggregatingProblemConsumer(eventConsumer, { new OperationIdentifier(1) })
        emitter.setThresholdForIntermediateSummary(thresholdForIntermediateSummary)

        when:
        for (int i = 0; i < thresholdForIntermediateSummary + 1; i++) {
            emitter.emit(createMockProblem("foo"))
        }

        then:
        2 * eventConsumer.progress(_ as InternalProblemEventVersion2)
    }

    def "send no intermediate event when only unique events where sent"() {
        given:
        def eventConsumer = Mock(ProgressEventConsumer)
        def emitter = new AggregatingProblemConsumer(eventConsumer, { new OperationIdentifier(1) })

        emitter.setThresholdForIntermediateSummary(thresholdForIntermediateSummary)

        when:
        for (int i = 0; i < thresholdForIntermediateSummary + 1; i++) {
            emitter.emit(createMockProblem("foo$i"))
        }

        then:
        (thresholdForIntermediateSummary + 1) * eventConsumer.progress(_ as InternalProblemEventVersion2)
    }

    private createMockProblem(String categoryName) {
        def problem = Mock(InternalProblemEventVersion2)
        def details = Mock(InternalBasicProblemDetailsVersion3)
        details.definition >> new DefaultProblemDefinition(
            new DefaultProblemId(categoryName, categoryName,
                new DefaultProblemGroup("group", "group", null)),
            new DefaultSeverity(0),
            new DefaultDocumentationLink("https://docs.example.org"))
        problem.details >> details
        problem
    }
}
