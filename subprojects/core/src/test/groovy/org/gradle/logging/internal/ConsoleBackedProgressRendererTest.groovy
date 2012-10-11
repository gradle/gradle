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

import org.gradle.internal.nativeplatform.console.ConsoleMetaData

class ConsoleBackedProgressRendererTest extends OutputSpecification {
    private final OutputEventListener listener = Mock()
    private final Console console = Mock()
    private final Label statusBar = Mock()
    private final StatusBarFormatter statusBarFormatter = new DefaultStatusBarFormatter(Mock(ConsoleMetaData))
    private final ConsoleBackedProgressRenderer renderer = new ConsoleBackedProgressRenderer(listener, console, statusBarFormatter)

    def setup() {
        (0..1) * console.getStatusBar() >> statusBar
    }

    def forwardsEventsToListener() {
        def event = event('message')

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
        0 * listener._
        0 * statusBar._
    }

    def statusBarTracksMostRecentOperationStatus() {
        when:
        renderer.onOutput(start(status: 'status'))

        then:
        1 * statusBar.setText('> status')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }

    def statusBarTracksOperationProgressForOperationWithNoStatus() {
        when:
        renderer.onOutput(start(status: ''))

        then:
        1 * statusBar.setText('')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }

    def statusBarTracksOperationProgressForOperationWithNoInitialStatus() {
        when:
        renderer.onOutput(start(status: ''))

        then:
        1 * statusBar.setText('')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }

    def statusBarTracksNestedOperationProgress() {
        when:
        renderer.onOutput(start(status: 'status'))

        then:
        1 * statusBar.setText('> status')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(start(status: 'status2'))

        then:
        1 * statusBar.setText('> progress > status2')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress2'))

        then:
        1 * statusBar.setText('> progress > progress2')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }

    def statusBarTracksNestedOperationProgressForOperationsWithNoInitialStatus() {
        when:
        renderer.onOutput(start(status: ''))
        renderer.onOutput(start(status: ''))

        then:
        2 * statusBar.setText('')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }

    def usesShortDescriptionWhenOperationHasNoStatus() {
        when:
        renderer.onOutput(start(shortDescription: 'short'))

        then:
        1 * statusBar.setText('> short')
        0 * statusBar._

        when:
        renderer.onOutput(progress('progress'))

        then:
        1 * statusBar.setText('> progress')
        0 * statusBar._

        when:
        renderer.onOutput(progress(''))

        then:
        1 * statusBar.setText('> short')
        0 * statusBar._

        when:
        renderer.onOutput(complete('complete'))

        then:
        1 * statusBar.setText('')
        0 * statusBar._
    }
}
