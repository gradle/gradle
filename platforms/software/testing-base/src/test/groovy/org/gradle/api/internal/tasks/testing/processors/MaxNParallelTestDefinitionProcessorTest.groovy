/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.processors


import org.gradle.api.internal.tasks.testing.TestDefinition
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.actor.Actor
import org.gradle.internal.actor.ActorFactory
import spock.lang.Specification
import org.gradle.internal.Factory

class MaxNParallelTestDefinitionProcessorTest extends Specification {
    private final Factory<TestDefinitionProcessor> factory = Mock()
    private final TestResultProcessor resultProcessor = Mock()
    private final TestResultProcessor asyncResultProcessor = Mock()
    private final Actor resultProcessorActor = Mock()
    private final ActorFactory actorFactory = Mock()
    private final MaxNParallelTestDefinitionProcessor processor = new MaxNParallelTestDefinitionProcessor(2, factory, actorFactory)

    def createsThreadSafeWrapperForResultProcessorOnStart() {
        when:
        processor.startProcessing(resultProcessor)

        then:
        1 * actorFactory.createActor(resultProcessor) >> resultProcessorActor
        1 * resultProcessorActor.getProxy(TestResultProcessor) >> asyncResultProcessor
    }

    def doesNothingWhenNoTestsProcessed() {
        startProcessor()

        when:
        processor.stop()

        then:
        0 * factory.create()
        1 * resultProcessorActor.stop()
    }

    def startProcessor() {
        1 * actorFactory.createActor(resultProcessor) >> resultProcessorActor
        1 * resultProcessorActor.getProxy(TestResultProcessor) >> asyncResultProcessor
        processor.startProcessing(resultProcessor)
    }

    def startsProcessorsOnDemandAndStopsAtEnd() {
        TestDefinition test = Mock()
        TestDefinitionProcessor processor1 = Mock()
        TestDefinitionProcessor asyncProcessor1 = Mock()
        Actor actor1 = Mock()

        startProcessor()

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor1
        1 * actorFactory.createActor(processor1) >> actor1
        1 * actor1.getProxy(TestDefinitionProcessor) >> asyncProcessor1
        1 * asyncProcessor1.startProcessing(asyncResultProcessor)
        1 * asyncProcessor1.processTestDefinition(test)

        when:
        processor.stop()

        then:
        1 * asyncProcessor1.stop()
        1 * actor1.stop()
        1 * resultProcessorActor.stop()
    }

    def startsMultipleProcessorsOnDemandAndStopsAtEnd() {
        TestDefinition test = Mock()
        TestDefinitionProcessor processor1 = Mock()
        TestDefinitionProcessor processor2 = Mock()
        TestDefinitionProcessor asyncProcessor1 = Mock()
        TestDefinitionProcessor asyncProcessor2 = Mock()
        Actor actor1 = Mock()
        Actor actor2 = Mock()

        startProcessor()

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor1
        1 * actorFactory.createActor(processor1) >> actor1
        1 * actor1.getProxy(TestDefinitionProcessor) >> asyncProcessor1
        1 * asyncProcessor1.startProcessing(asyncResultProcessor)
        1 * asyncProcessor1.processTestDefinition(test)

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor2
        1 * actorFactory.createActor(processor2) >> actor2
        1 * actor2.getProxy(TestDefinitionProcessor) >> asyncProcessor2
        1 * asyncProcessor2.startProcessing(asyncResultProcessor)
        1 * asyncProcessor2.processTestDefinition(test)

        when:
        processor.stop()

        then:
        1 * asyncProcessor1.stop()
        1 * asyncProcessor2.stop()
    }

    def roundRobinsTestClassesToProcessors() {
        TestDefinition test = Mock()
        TestDefinitionProcessor processor1 = Mock()
        TestDefinitionProcessor processor2 = Mock()
        TestDefinitionProcessor asyncProcessor1 = Mock()
        TestDefinitionProcessor asyncProcessor2 = Mock()
        Actor actor1 = Mock()
        Actor actor2 = Mock()

        startProcessor()

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor1
        1 * actorFactory.createActor(processor1) >> actor1
        1 * actor1.getProxy(TestDefinitionProcessor) >> asyncProcessor1
        1 * asyncProcessor1.startProcessing(asyncResultProcessor)
        1 * asyncProcessor1.processTestDefinition(test)

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor2
        1 * actorFactory.createActor(processor2) >> actor2
        1 * actor2.getProxy(TestDefinitionProcessor) >> asyncProcessor2
        1 * asyncProcessor2.startProcessing(asyncResultProcessor)
        1 * asyncProcessor2.processTestDefinition(test)

        when:
        processor.processTestDefinition(test)

        then:
        1 * asyncProcessor1.processTestDefinition(test)

        when:
        processor.processTestDefinition(test)

        then:
        1 * asyncProcessor2.processTestDefinition(test)
    }

    def "stopNow propagates to factory created processors"() {
        TestDefinition test = Mock()
        TestDefinitionProcessor processor1 = Mock()
        TestDefinitionProcessor processor2 = Mock()
        TestDefinitionProcessor asyncProcessor1 = Mock()
        TestDefinitionProcessor asyncProcessor2 = Mock()
        Actor actor1 = Mock()
        Actor actor2 = Mock()

        startProcessor()

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor1
        1 * actorFactory.createActor(processor1) >> actor1
        1 * actor1.getProxy(TestDefinitionProcessor) >> asyncProcessor1

        when:
        processor.processTestDefinition(test)

        then:
        1 * factory.create() >> processor2
        1 * actorFactory.createActor(processor2) >> actor2
        1 * actor2.getProxy(TestDefinitionProcessor) >> asyncProcessor2

        when:
        processor.stopNow()

        then:
        1 * processor1.stopNow()
        1 * processor2.stopNow()
    }
}
