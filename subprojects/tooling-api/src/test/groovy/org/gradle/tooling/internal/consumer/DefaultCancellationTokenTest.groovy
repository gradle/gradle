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

package org.gradle.tooling.internal.consumer

import spock.lang.Specification

class DefaultCancellationTokenTest extends Specification {
    def 'can cancel token'() {
        def source = new DefaultCancellationTokenSource()

        when:
        def token = source.token()

        then:
        !token.cancellationRequested

        when:
        source.cancel()

        then:
        token.cancellationRequested
    }

    def 'cancel notifies callbacks'() {
        def source = new DefaultCancellationTokenSource()

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        def token = source.token()
        token.addCallback(callback1)
        token.addCallback(callback2)

        when:
        source.cancel()

        then:
        token.cancellationRequested
        1 * callback1.run()
        1 * callback2.run()
    }

    def 'addCallback after cancel notifies'() {
        def source = new DefaultCancellationTokenSource()

        def callback = Mock(Runnable)
        def token = source.token()
        source.cancel()

        when:
        token.addCallback(callback)

        then:
        token.cancellationRequested
        1 * callback.run()
    }

    def 'cancel drops references'() {
        def source = new DefaultCancellationTokenSource()

        def callback1 = Mock(Runnable)
        def token = source.token()
        token.addCallback(callback1)

        when:
        source.cancel()

        then:
        token.cancellationRequested
        1 * callback1.run()
        token.callbacks.empty
    }

    def 'cancel notifies callbacks even if exception is thrown'() {
        def source = new DefaultCancellationTokenSource()
        def ex = new IllegalStateException('testing')

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        def token = source.token()
        token.addCallback(callback1)
        token.addCallback(callback2)

        when:
        source.cancel()

        then:
        RuntimeException e = thrown()
        e.cause == ex
        token.cancellationRequested

        and:
        1 * callback1.run() >> { throw ex }
        1 * callback2.run()
    }

    def 'cancel notification stop when error is encountered'() {
        def source = new DefaultCancellationTokenSource()
        def ex = new Error('testing')

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        def token = source.token()
        token.addCallback(callback1)
        token.addCallback(callback2)

        when:
        source.cancel()

        then:
        Error e = thrown()
        e == ex
        token.cancellationRequested

        and:
        1 * callback1.run() >> { throw ex }
        0 * callback2.run()
    }

    def 'cancel notifies callbacks and preserves exceptions'() {
        def source = new DefaultCancellationTokenSource()
        def ex1 = new IllegalStateException('testing', new IOException('something happened'))
        def ex2 = new IllegalStateException('testing')

        def callback1 = Mock(Runnable)
        def callback2 = Mock(Runnable)
        def callback3 = Mock(Runnable)
        def token = source.token()
        token.addCallback(callback1)
        token.addCallback(callback2)
        token.addCallback(callback3)

        when:
        source.cancel()

        then:
        RuntimeException e = thrown()
        containsExceptionAsCause(e, ex1)
        containsExceptionAsCause(e, ex2)
        token.cancellationRequested

        and:
        1 * callback1.run() >> { throw ex1 }
        1 * callback2.run() >> { throw ex2 }
        1 * callback3.run()
    }

    def containsExceptionAsCause(def thrown, def cause) {
        def e = thrown
        while (e != null) {
            if (e == cause) {
                return true
            }
            e = e.cause
        }
        false
    }
}
