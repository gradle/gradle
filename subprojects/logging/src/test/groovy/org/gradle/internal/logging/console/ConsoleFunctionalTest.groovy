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

package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.OperationIdentifier
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.sink.OutputEventRenderer
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.gradle.internal.progress.BuildOperationType
import org.gradle.internal.time.TimeProvider
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.RedirectStdOutAndErr
import org.junit.Rule
import spock.lang.Specification

class ConsoleFunctionalTest extends Specification {
    @Rule public final RedirectStdOutAndErr outputs = new RedirectStdOutAndErr()
    private final ConsoleStub console = new ConsoleStub()
    private final TimeProvider timeProvider = Mock(TimeProvider)
    private final ConsoleMetaData metaData = Mock(ConsoleMetaData)
    private OutputEventRenderer renderer
    private long currentTimeMs;
    private static final String IDLE = '> IDLE'
    private static final String CATEGORY = 'CATEGORY'

    def setup() {
        renderer = new OutputEventRenderer(timeProvider)
        renderer.configure(LogLevel.INFO)
        renderer.addConsole(console, true, true, metaData)
        _ * metaData.getRows() >> 10
        _ * metaData.getCols() >> 35
        _ * timeProvider.getCurrentTime() >> { currentTimeMs }
    }

    def "renders initial state"() {
        when:
        renderer.onOutput(startEvent(1, null, BuildStatusRenderer.BUILD_PROGRESS_CATEGORY, 'INITIALIZATION PHASE', '<---> 0% INITIALIZING'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert statusBar.display == '<---> 0% INITIALIZING [0s]'
            assert progressArea.display == []
        }
    }

    def "renders configuration progress"() {
        when:
        renderer.onOutput(startEvent(1, null, BuildStatusRenderer.BUILD_PROGRESS_CATEGORY, 'CONFIGURATION PHASE', '<---> 0% CONFIGURING'))
        renderer.onOutput(startEvent(2, 1, 'category', 'Configuring root project', 'root project', null, 'root project'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert statusBar.display == '<---> 0% CONFIGURING [0s]'
            assert progressArea.display == ['> root project']
        }

        when:
        currentTimeMs += 2000L;
        renderer.onOutput(completeEvent(2, BuildStatusRenderer.BUILD_PROGRESS_CATEGORY))
        renderer.onOutput(progressEvent(1, '<=--> 33% CONFIGURING'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert statusBar.display == '<=--> 33% CONFIGURING [2s]'
            assert progressArea.display == [IDLE]
        }
    }

    def "removes operation display from progress area upon completion"() {
        when:
        renderer.onOutput(startEvent(2, ':foo'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :foo']
        }

        when:
        renderer.onOutput(completeEvent(2, BuildStatusRenderer.BUILD_PROGRESS_CATEGORY))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == [IDLE]
        }
    }

    def "renders parallel execution progress"() {
        when:
        renderer.onOutput(startEvent(2, ':foo'))
        renderer.onOutput(startEvent(3, ':bar'))
        renderer.onOutput(startEvent(4, ':baz'))
        renderer.onOutput(startEvent(5, ':quux'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :foo', '> :bar', '> :baz', '> :quux']
        }
    }

    def "renders child operation work inline"() {
        when:
        renderer.onOutput(startEvent(1, ':foo'))
        renderer.onOutput(startEvent(2, 1, null, null, null, null, ':bar'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :foo > :bar']
        }
    }

    def "trims progress display to console width"() {
        given:
        _ * metaData.getCols() >> 25

        when:
        renderer.onOutput(startEvent(1, 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJK'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> abcdefghijklmnopqrstuvwxyzABCDEF']
        }
    }

    def "progress display is removed upon build completion"() {
        when:
        renderer.onOutput(startEvent(1, null, BuildStatusRenderer.BUILD_PROGRESS_CATEGORY, 'EXECUTION PHASE', '<---> 0% EXECUTING'))
        renderer.onOutput(startEvent(2, ':foo'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.visible
        }

        when:
        renderer.onOutput(new EndOutputEvent())

        then:
        ConcurrentTestUtil.poll(1) {
            assert !progressArea.visible
        }
    }

    def "preserves position of operations in progress"() {
        when:
        renderer.onOutput(startEvent(1, ':wat'))
        renderer.onOutput(startEvent(2, ':foo'))
        renderer.onOutput(startEvent(3, ':bar'))
        renderer.onOutput(startEvent(4, ':baz'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :wat', '> :foo', '> :bar', '> :baz']
        }

        when:
        renderer.onOutput(completeEvent(1, ':wat'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == [IDLE, '> :foo', '> :bar', '> :baz']
        }

        when:
        renderer.onOutput(progressEvent(2, '[35 / 50] :foo'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == [IDLE, '> [35 / 50] :foo', '> :bar', '> :baz']
        }

        when:
        renderer.onOutput(startEvent(5, ':new'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :new', '> [35 / 50] :foo', '> :bar', '> :baz']
        }
    }

    def "progress display height remains fixed even if more work in progress than max height"() {
        when:
        renderer.onOutput(startEvent(1, ':wat'))
        renderer.onOutput(startEvent(2, ':foo'))
        renderer.onOutput(startEvent(3, ':bar'))
        renderer.onOutput(startEvent(4, ':baz'))
        renderer.onOutput(startEvent(5, ':foz'))
        renderer.onOutput(startEvent(6, ':nope'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :wat', '> :foo', '> :bar', '> :baz', '> :foz']
        }
        progressArea.buildProgressLabelCount == (metaData.rows / 2).toInteger()

        when:
        renderer.onOutput(completeEvent(1, ':wat'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :nope', '> :foo', '> :bar', '> :baz', '> :foz']
        }
        progressArea.buildProgressLabelCount == (metaData.rows / 2).toInteger()
    }

    def "progress display height scales when more work in progress are added"() {
        when:
        renderer.onOutput(startEvent(1, ':wat'))
        renderer.onOutput(startEvent(2, ':foo'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :wat', '> :foo']
        }

        and:
        renderer.onOutput(startEvent(3, ':bar'))
        renderer.onOutput(startEvent(4, ':baz'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :wat', '> :foo', '> :bar', '> :baz']
        }

        and:
        renderer.onOutput(completeEvent(1, ':wat'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == [IDLE, '> :foo', '> :bar', '> :baz']
        }
    }

    def "progress display height doesn't scale back down when complete events are received"() {
        when:
        renderer.onOutput(startEvent(1, ':wat'))
        renderer.onOutput(startEvent(2, ':foo'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> :wat', '> :foo']
        }

        and:
        renderer.onOutput(completeEvent(2, ':foo'))
        renderer.onOutput(completeEvent(1, ':wat'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == [IDLE, IDLE]
        }
    }

    def "multiple events for same operation are coalesced and rendered once"() {
        when:
        renderer.onOutput(startEvent(1, ':wat'))
        renderer.onOutput(progressEvent(1, '[1 / 7] :wat'))
        renderer.onOutput(progressEvent(1, '[2 / 7] :wat'))
        renderer.onOutput(progressEvent(1, '[3 / 7] :wat'))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> [3 / 7] :wat']
        }
    }

    def "operation that finishes immediately is not rendered"() {
        when:
        [startEvent(1, ':wat'), progressEvent(1, ':wat'), completeEvent(1, ':wat')].each {
            renderer.onOutput(it)
        }

        then:
        ConcurrentTestUtil.poll(1, 0.1) {
            assert progressArea.display == []
        }
    }

    def "progress display uses short description if status is empty"() {
        when:
        renderer.onOutput(startEvent(1, null, 'CATEGORY', 'DESCRIPTION', 'SHORT_DESCRIPTION', 'LOGGING_HEADER', ''))

        then:
        ConcurrentTestUtil.poll(1) {
            assert progressArea.display == ['> SHORT_DESCRIPTION']
        }
    }

    ProgressStartEvent startEvent(Long id, Long parentId=null, category='CATEGORY', description='DESCRIPTION', shortDescription='SHORT_DESCRIPTION', loggingHeader='LOGGING_HEADER', status='STATUS') {
        long timestamp = timeProvider.currentTime
        OperationIdentifier parent = parentId ? new OperationIdentifier(parentId) : null
        new ProgressStartEvent(new OperationIdentifier(id), parent, timestamp, category, description, shortDescription, loggingHeader, status, null, null, BuildOperationType.UNCATEGORIZED)
    }

    ProgressStartEvent startEvent(Long id, String status) {
        new ProgressStartEvent(new OperationIdentifier(id), null, timeProvider.currentTime, null, null, null, null, status, null, null, BuildOperationType.UNCATEGORIZED)
    }

    ProgressEvent progressEvent(Long id, status='STATUS') {
        new ProgressEvent(new OperationIdentifier(id), status)
    }

    ProgressCompleteEvent completeEvent(Long id, status='STATUS') {
        long timestamp = timeProvider.currentTime
        new ProgressCompleteEvent(new OperationIdentifier(id), timestamp, status)
    }

    private ConsoleStub.TestableRedrawableLabel getStatusBar() {
        console.statusBar as ConsoleStub.TestableRedrawableLabel
    }

    private ConsoleStub.TestableBuildProgressTextArea getProgressArea() {
        console.buildProgressArea as ConsoleStub.TestableBuildProgressTextArea
    }
}
