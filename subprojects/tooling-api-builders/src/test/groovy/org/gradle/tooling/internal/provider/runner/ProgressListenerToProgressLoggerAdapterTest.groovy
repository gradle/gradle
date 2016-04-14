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

package org.gradle.tooling.internal.provider.runner

import groovy.transform.TupleConstructor
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.ProgressEvent
import spock.lang.Specification
import spock.lang.Subject

class ProgressListenerToProgressLoggerAdapterTest extends Specification {
    ProgressLoggerFactory progressLoggerFactory = Mock()
    @Subject
    ProgressListenerToProgressLoggerAdapter adapter = new ProgressListenerToProgressLoggerAdapter(progressLoggerFactory)

    @TupleConstructor
    static class SimpleProgressEvent implements ProgressEvent {
        String description
    }

    def "can adapt a single status"() {
        given:
        ProgressLogger progressLogger = Mock()

        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.start('A', 'A')
        then:
        1 * progressLogger.completed()
        then:
        0 * _._
    }

    def "can adapt a hierarchical callstack"() {
        given:
        ProgressLogger progressLoggerA = Mock()
        ProgressLogger progressLoggerB = Mock()

        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerA
        1 * progressLoggerA.start('A', 'A')
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerB
        1 * progressLoggerB.start('B', 'B')
        then:
        1 * progressLoggerB.completed()
        then:
        1 * progressLoggerA.completed()
        then:
        0 * _._
    }

    def "can adapt a looping call stack"() {
        given:
        ProgressLogger progressLoggerA = Mock()
        ProgressLogger progressLoggerB = Mock()
        ProgressLogger progressLoggerC1 = Mock()
        ProgressLogger progressLoggerC2 = Mock()
        ProgressLogger progressLoggerC3 = Mock()

        when:
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('C'))
        adapter.statusChanged(new SimpleProgressEvent('B'))
        adapter.statusChanged(new SimpleProgressEvent('A'))
        adapter.statusChanged(new SimpleProgressEvent(''))

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerA
        1 * progressLoggerA.start('A', 'A')
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerB
        1 * progressLoggerB.start('B', 'B')
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerC1
        1 * progressLoggerC1.start('C', 'C')
        then:
        1 * progressLoggerC1.completed()
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerC2
        1 * progressLoggerC2.start('C', 'C')
        then:
        1 * progressLoggerC2.completed()
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLoggerC3
        1 * progressLoggerC3.start('C', 'C')
        then:
        1 * progressLoggerC3.completed()
        then:
        1 * progressLoggerB.completed()
        then:
        1 * progressLoggerA.completed()
        then:
        0 * _._
    }

}
