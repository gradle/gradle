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
package org.gradle.logging.internal

class ProgressLogEventGeneratorTest extends OutputSpecification {
    private final OutputEventListener target = Mock()

    def insertsLogMessagesWhenOperationStartsAndEnds() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)
        def startEvent = start('description')
        def completeEvent = complete('status')

        when:
        generator.onOutput(startEvent)

        then:
        1 * target.onOutput(!null) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans[0].text == 'description'
            assert event.timestamp == startEvent.timestamp
        }
        0 * target._

        when:
        generator.onOutput(completeEvent)

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative(' ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
            assert event.timestamp == completeEvent.timestamp
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationStartsAndEndsAndDeferredHeaderMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)
        def startEvent = start('description')
        def completeEvent = complete('status')

        when:
        generator.onOutput(startEvent)

        then:
        0 * target._

        when:
        generator.onOutput(completeEvent)

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
            assert event.timestamp == completeEvent.timestamp
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationGeneratesSomeOutput() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)
        def logEvent = event('message')

        when:
        generator.onOutput(start('description'))

        then:
        1 * target.onOutput(!null) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == 'description'
        }
        0 * target._

        when:
        generator.onOutput(logEvent)

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent}) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == toNative('\n')
        }
        1 * target.onOutput(logEvent)
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationGeneratesSomeOutputAndInDeferredHeaderMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)
        def logEvent = event('message')
        def startEvent = start('description')
        def completeEvent = complete('status')

        when:
        generator.onOutput(startEvent)

        then:
        0 * target._

        when:
        generator.onOutput(logEvent)

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent}) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == toNative('description\n')
            assert event.timestamp == startEvent.timestamp
        }
        1 * target.onOutput(logEvent)
        0 * target._

        when:
        generator.onOutput(completeEvent)

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationsAreNested() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)

        when:
        generator.onOutput(start('description'))

        then:
        1 * target.onOutput(!null) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == toNative('description')
        }
        0 * target._

        when:
        generator.onOutput(start('description2'))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('\n')}) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == toNative('\n')
        }
        1 * target.onOutput(!null) >> { args->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 1
            assert event.spans[0].text == toNative('description2')
        }
        0 * target._

        when:
        generator.onOutput(complete('status2'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative(' ')
            assert event.spans[1].text == toNative('status2')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationsAreNestedAndInDeferredMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)

        when:
        generator.onOutput(start('description'))
        generator.onOutput(start('description2'))

        then:
        0 * target._

        when:
        generator.onOutput(complete('status2'))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('description\n')})
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description2 ')
            assert event.spans[1].text == toNative('status2')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative('description ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoFinalStatus() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)

        when:
        generator.onOutput(start('description'))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == 'description'})
        0 * target._

        when:
        generator.onOutput(complete(''))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('\n')})
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoFinalStatusAndInDeferredMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)

        when:
        generator.onOutput(start('description'))

        then:
        0 * target._

        when:
        generator.onOutput(complete(''))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('description\n')})
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoDescription() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)

        when:
        generator.onOutput(start(''))

        then:
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 2
            assert event.spans[0].text == toNative('status')
            assert event.spans[1].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoDescriptionWhenInDeferredMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)

        when:
        generator.onOutput(start(''))

        then:
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 2
            assert event.spans[0].text == toNative('status')
            assert event.spans[1].text == toNative('\n')
        }
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoDescriptionOrFinalStatus() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)

        when:
        generator.onOutput(start(''))

        then:
        0 * target._

        when:
        generator.onOutput(complete(''))

        then:
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoFinalStatusAndGeneratesLogMessages() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)
        def event = event('event')

        when:
        generator.onOutput(start('description'))
        generator.onOutput(event)

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('description')})
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('\n')})
        1 * target.onOutput(event)
        0 * target._

        when:
        generator.onOutput(complete(''))

        then:
        0 * target._
    }

    def insertsLogMessagesWhenOperationHasNoFinalStatusAndGeneratesLogMessagesAndInDeferredMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)
        def event = event('event')

        when:
        generator.onOutput(start('description'))
        generator.onOutput(event)

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('description\n')})
        1 * target.onOutput(event)
        0 * target._

        when:
        generator.onOutput(complete(''))

        then:
        0 * target._
    }

    def insertsLogMessagesWhenOperationsAreNestedAndHaveNoDescriptionAndInDeferredMode() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, true)

        when:
        generator.onOutput(start(''))
        generator.onOutput(start(''))

        then:
        0 * target._

        when:
        generator.onOutput(complete('status2'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 2
            assert event.spans[0].text == toNative('status2')
            assert event.spans[1].text == toNative('\n')
        }
        0 * target._

        when:
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 2
            assert event.spans[0].text == toNative('status')
            assert event.spans[1].text == toNative('\n')
        }
        0 * target._
    }
    
    def ignoresProgressEvents() {
        ProgressLogEventGenerator generator = new ProgressLogEventGenerator(target, false)

        when:
        generator.onOutput(start('description'))
        generator.onOutput(progress('tick'))
        generator.onOutput(complete('status'))

        then:
        1 * target.onOutput({it instanceof StyledTextOutputEvent && it.spans[0].text == toNative('description')})
        1 * target.onOutput(!null) >> { args ->
            StyledTextOutputEvent event = args[0]
            assert event.spans.size() == 3
            assert event.spans[0].text == toNative(' ')
            assert event.spans[1].text == toNative('status')
            assert event.spans[2].text == toNative('\n')
        }
        0 * target._
    }
}
