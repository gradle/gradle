/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization

import org.gradle.internal.exceptions.DefaultMultiCauseException
import spock.lang.Specification

class DefaultBuildCancellationTokenSpec extends Specification {
    def 'can cancel token'() {
        when:
        def token = new DefaultBuildCancellationToken()

        then:
        !token.cancellationRequested

        when:
        token.cancel()

        then:
        token.cancellationRequested
    }

    def 'cancel notifies callbacks'() {
        def token = new DefaultBuildCancellationToken()

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        token.addCallback(callback1)
        token.addCallback(callback2)

        when:
        token.cancel()

        then:
        token.cancellationRequested
        1 * callback1.run()
        1 * callback2.run()
    }

    def 'addCallback after cancel notifies'() {
        def token = new DefaultBuildCancellationToken()

        def callback = Mock(Runnable)
        token.cancel()

        when:
        token.addCallback(callback)

        then:
        token.cancellationRequested
        1 * callback.run()
    }

    def 'cancel drops references'() {
        def token = new DefaultBuildCancellationToken()

        def callback1 = Mock(Runnable)
        token.addCallback(callback1)

        when:
        token.cancel()

        then:
        token.cancellationRequested
        1 * callback1.run()
        token.callbacks.empty
    }

    def 'cancel notifies callbacks even if exception is thrown'() {
        def token = new DefaultBuildCancellationToken()
        def ex = new IllegalStateException('testing')

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        token.addCallback(callback1)
        token.addCallback(callback2)

        when:
        token.cancel()

        then:
        RuntimeException e = thrown()
        e.cause == ex
        token.cancellationRequested

        and:
        1 * callback1.run() >> { throw ex }
        1 * callback2.run()
    }

    def 'cancel notifies callbacks and preserves exceptions'() {
        def token = new DefaultBuildCancellationToken()
        def ex1 = new IllegalStateException('testing', new IOException('something happened'))
        def ex2 = new IllegalStateException('testing')

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        def callback3 = Mock(Runnable)
        token.addCallback(callback1)
        token.addCallback(callback2)
        token.addCallback(callback3)

        when:
        token.cancel()

        then:
        DefaultMultiCauseException e = thrown()
        e.causes == [ex1, ex2]
        token.cancellationRequested

        and:
        1 * callback1.run() >> { throw ex1 }
        1 * callback2.run() >> { throw ex2 }
        1 * callback3.run()
    }

    def 'removed callback is not notified'() {
        def token = new DefaultBuildCancellationToken()

        def callback = Mock(Runnable)
        token.addCallback(callback)
        token.removeCallback(callback)

        when:
        token.cancel()

        then:
        token.cancellationRequested
        0 * callback.run()
    }
}
