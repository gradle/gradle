/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.LogGroupHeaderEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.OutputEventGroup
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.format.LogHeaderFormatter
import org.gradle.internal.progress.BuildOperationCategory

class HeaderPrependingOutputEventGroupListenerTest extends OutputSpecification {
    public static final String LOG_HEADER = 'LOG_HEADER'
    public static final String DESCRIPTION = 'DESCRIPTION'
    public static final String SHORT_DESCRIPTION = 'SHORT_DESC'
    public static final String STATUS = 'STATUS'
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)

    def logHeaderFormatter = new LogHeaderFormatter() {
        List<StyledTextOutputEvent.Span> format(String h, String d, String s, String status, boolean failed) {
            [new StyledTextOutputEvent.Span("Header $d")]
        }
    }

    HeaderPrependingOutputEventGroupListener listener = new HeaderPrependingOutputEventGroupListener(downstreamListener, logHeaderFormatter)

    def "generates header when last seen build operation does not match given one"() {
        given:
        def buildOpId = new OperationIdentifier(1)
        def spans = [new StyledTextOutputEvent.Span("Header $DESCRIPTION")]
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, buildOpId)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, false, [warningMessage], buildOpId, BuildOperationCategory.TASK))

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header $DESCRIPTION</Normal>".toString() })
        1 * downstreamListener.onOutput(warningMessage)
        0 * downstreamListener._

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, false, [], new OperationIdentifier(2), BuildOperationCategory.TASK))

        then:
        1 * downstreamListener.onOutput({ it.toString() == "[LIFECYCLE] [category] <Normal>Header $DESCRIPTION</Normal>".toString() })
        0 * downstreamListener._
    }

    def "generates spacer line output event given ungrouped output following grouped output"() {
        given:
        def buildOpId = new OperationIdentifier(1)
        LogEvent ungroupedLogMessage = new LogEvent(tenAm, CATEGORY, LogLevel.INFO, "UNGROUPED LOG MESSAGE", null)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, false, [], buildOpId, BuildOperationCategory.TASK))
        listener.onOutput(ungroupedLogMessage)

        then:
        1 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)
        1 * downstreamListener.onOutput({ it.message == "" })
        1 * downstreamListener.onOutput(ungroupedLogMessage)
        0 * downstreamListener._
    }

    def "does not generate header if given output matches last seen build operation"() {
        given:
        def buildOpId = new OperationIdentifier(1)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, false, [event("LOG WARNING", LogLevel.WARN, buildOpId)], buildOpId, BuildOperationCategory.TASK))

        then:
        1 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, false, [event("LOG MESSAGE", LogLevel.LIFECYCLE, buildOpId)], buildOpId, BuildOperationCategory.TASK))

        then:
        0 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)
    }

    def "does not generate header when propagating output not associated with a build operation"() {
        given:
        def event1 = event("LOG EVENT 1", LogLevel.DEBUG, null)
        def event2 = event("LOG EVENT 2", LogLevel.ERROR, null)

        when:
        listener.onOutput(event1)
        listener.onOutput(event2)

        then:
        1 * downstreamListener.onOutput(event1)
        1 * downstreamListener.onOutput(event2)
        0 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)
    }

    def "forwards all events unchanged"() {
        given:
        def startEvent = start(1)
        def progressEvent = progress('PROGRESS_STATUS')
        def completeEvent = complete(1)
        def endOutputEvent = new EndOutputEvent()

        when:
        listener.onOutput(startEvent)
        listener.onOutput(progressEvent)
        listener.onOutput(completeEvent)
        listener.onOutput(endOutputEvent)

        then:
        1 * downstreamListener.onOutput(startEvent)
        1 * downstreamListener.onOutput(progressEvent)
        1 * downstreamListener.onOutput(completeEvent)
        1 * downstreamListener.onOutput(endOutputEvent)
        0 * _
    }
}
