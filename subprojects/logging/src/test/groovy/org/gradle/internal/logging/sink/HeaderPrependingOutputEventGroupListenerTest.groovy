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
import spock.lang.Subject

class HeaderPrependingOutputEventGroupListenerTest extends OutputSpecification {
    public static final String LOG_HEADER = 'LOG_HEADER'
    public static final String DESCRIPTION = 'DESCRIPTION'
    public static final String SHORT_DESCRIPTION = 'SHORT_DESC'
    public static final String STATUS = 'STATUS'
    private final OutputEventListener downstreamListener = Mock(OutputEventListener)
    def logHeaderFormatter = Mock(LogHeaderFormatter)

    @Subject listener = new HeaderPrependingOutputEventGroupListener(downstreamListener, logHeaderFormatter)

    def setup() {
        logHeaderFormatter.format(_, _, _, _) >> { h, d, s, st -> [new StyledTextOutputEvent.Span("Header $d")] }
    }

    def "generates header when last seen build operation does not match given one"() {
        given:
        def buildOpId = new OperationIdentifier(1)
        def spans = [new StyledTextOutputEvent.Span("Header $DESCRIPTION")]
        def warningMessage = event('Warning: some deprecation or something', LogLevel.WARN, buildOpId)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, [warningMessage], buildOpId, BuildOperationCategory.TASK))

        then:
        1 * logHeaderFormatter.format(LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS) >> { spans }
        1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $DESCRIPTION</Normal>".toString() })
        1 * downstreamListener.onOutput(warningMessage)
        0 * _

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, [], new OperationIdentifier(2), BuildOperationCategory.TASK))

        then:
        1 * logHeaderFormatter.format(LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS) >> { spans }
        1 * downstreamListener.onOutput({ it.toString() == "[null] [category] <Normal>Header $DESCRIPTION</Normal>".toString() })
        0 * _
    }

    def "generates spacer line output event given ungrouped output following grouped output"() {
        given:
        def buildOpId = new OperationIdentifier(1)
        def ungroupedLogMessage = event("UNGROUPED LOG MESSAGE", LogLevel.INFO, new OperationIdentifier(3))

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, [], buildOpId, BuildOperationCategory.TASK))

        then:
        1 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)
        0 * downstreamListener._

        when:
        listener.onOutput(ungroupedLogMessage)

        then:
        1 * downstreamListener.onOutput({ it.message == "" })
        1 * downstreamListener.onOutput(ungroupedLogMessage)
        0 * _
    }

    def "does not generate header if given output matches last seen build operation"() {
        given:
        def buildOpId = new OperationIdentifier(1)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, [event("LOG WARNING", LogLevel.WARN, buildOpId)], buildOpId, BuildOperationCategory.TASK))

        then:
        1 * downstreamListener.onOutput(_ as LogGroupHeaderEvent)

        when:
        listener.onOutput(new OutputEventGroup(tenAm, CATEGORY, LOG_HEADER, DESCRIPTION, SHORT_DESCRIPTION, STATUS, [event("LOG MESSAGE", LogLevel.LIFECYCLE, buildOpId)], buildOpId, BuildOperationCategory.TASK))

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

    def testIsNewOutputGroup() {
        given:
        def buildOpId = new OperationIdentifier(1)

        expect:
        !listener.isNewOutputGroup(new EndOutputEvent(), null)
        !listener.isNewOutputGroup(new EndOutputEvent(), buildOpId)
        !listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, null), null)
        !listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, buildOpId), buildOpId)
        !listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, buildOpId), buildOpId)
        !listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, buildOpId), new OperationIdentifier(1))

        listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, buildOpId), new OperationIdentifier(2))
        listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, buildOpId), null)
        listener.isNewOutputGroup(new LogEvent(tenAm, CATEGORY, LogLevel.LIFECYCLE, "MESSAGE", null, null), buildOpId)
    }
}
