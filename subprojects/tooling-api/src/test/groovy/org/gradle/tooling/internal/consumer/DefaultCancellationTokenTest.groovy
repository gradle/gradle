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
}
