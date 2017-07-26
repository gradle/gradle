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

package org.gradle.internal.filewatch

import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

import static org.gradle.internal.filewatch.FileWatcherEvent.Type.*


class DefaultFileWatcherEventListenerTest extends Specification {
    def "should report individual changes when there are less than 4 changes"() {
        given:
        def events = [new FileWatcherEvent(CREATE, new File('/a/b/c1')), new FileWatcherEvent(CREATE, new File('/a/b/c2')), new FileWatcherEvent(CREATE, new File('/a/b/c3'))]
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "new file", events[0].file.absolutePath)
        then:
        1 * logger.formatln("%s: %s", "new file", events[1].file.absolutePath)
        then:
        1 * logger.formatln("%s: %s", "new file", events[2].file.absolutePath)
        and:
        0 * logger._
    }

    def "should report number of additional changes when there are more than 3 changes"() {
        given:
        def events = (1..100).collect {
            def file = new File("/a/b/c${it}")
            [new FileWatcherEvent(CREATE, file), new FileWatcherEvent(MODIFY, file)]
        }.flatten()
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "new file", events[0].file.absolutePath)
        then:
        1 * logger.formatln("%s: %s", "new file", events[2].file.absolutePath)
        then:
        1 * logger.formatln("%s: %s", "new file", events[4].file.absolutePath)
        then:
        1 * logger.formatln('and some more changes')
        and:
        0 * logger._
    }

    def "should not log anything when an empty list is given as input"() {
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        changeReporter.reportChanges(logger)
        then:
        0 * logger._
    }

    def "should suppress duplicate events and report the last"() {
        given:
        def events = [new FileWatcherEvent(CREATE, new File('a')), new FileWatcherEvent(CREATE, new File('a')), new FileWatcherEvent(DELETE, new File('a'))]
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "deleted", events[0].file.absolutePath)
        and:
        0 * logger._
    }

    def "should update the event type of the first 3 events if it changes"() {
        given:
        def events = (1..100).collect {
            def file = new File("${it}")
            [new FileWatcherEvent(CREATE, file), new FileWatcherEvent(MODIFY, file)]
        }.flatten()
        events << new FileWatcherEvent(DELETE, events[0].file)
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "deleted", events[0].file.absolutePath)
        1 * logger.formatln("%s: %s", "new file", events[2].file.absolutePath)
        1 * logger.formatln("%s: %s", "new file", events[4].file.absolutePath)
        1 * logger.formatln('and some more changes')
        0 * logger._
    }

    def "should report as created and ignore modification events"() {
        given:
        def events = [new FileWatcherEvent(CREATE, new File('a')), new FileWatcherEvent(MODIFY, new File('a')), new FileWatcherEvent(MODIFY, new File('a'))]
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "new file", events[0].file.absolutePath)
        and:
        0 * logger._
    }

    def "should ignore undefined type"() {
        given:
        def events = [new FileWatcherEvent(CREATE, new File('a')), new FileWatcherEvent(UNDEFINED, null), new FileWatcherEvent(MODIFY, new File('a')), new FileWatcherEvent(MODIFY, new File('a')), new FileWatcherEvent(UNDEFINED, null)]
        def logger = Mock(StyledTextOutput)
        def changeReporter = new DefaultFileWatcherEventListener()
        when:
        events.each { changeReporter.onChange(it) }
        changeReporter.reportChanges(logger)
        then:
        1 * logger.formatln("%s: %s", "new file", events[0].file.absolutePath)
        and:
        0 * logger._
    }
}
