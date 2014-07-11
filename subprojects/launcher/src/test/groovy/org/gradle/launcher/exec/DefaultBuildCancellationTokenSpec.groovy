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

package org.gradle.launcher.exec

import spock.lang.Specification

class DefaultBuildCancellationTokenSpec extends Specification {
    def 'can cancel token'() {
        when:
        def token = new DefaultBuildCancellationToken()

        then:
        !token.cancellationRequested

        when:
        token.doCancel()

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
        token.doCancel()

        then:
        token.cancellationRequested
        1 * callback1.run()
        1 * callback2.run()
    }

    def 'addCallback after cancel notifies'() {
        def token = new DefaultBuildCancellationToken()

        def callback = Mock(Runnable)
        token.doCancel()

        when:
        token.addCallback(callback)

        then:
        token.cancellationRequested
        1 * callback.run()
    }
}
