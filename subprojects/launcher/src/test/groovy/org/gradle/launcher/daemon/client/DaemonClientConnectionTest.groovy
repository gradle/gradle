/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.client

import org.gradle.messaging.remote.internal.Connection
import spock.lang.Specification

class DaemonClientConnectionTest extends Specification {

    final delegate = Mock(Connection)
    final onFailure = Mock(Runnable)
    final connection = new DaemonClientConnection(delegate, 'id', onFailure)

    def "stops"() {
        when:
        connection.stop()
        then:
        1 * delegate.stop()
        0 * onFailure.run()

        when:
        connection.requestStop()
        then:
        1 * delegate.requestStop()
        0 * onFailure.run()
    }

    def "dispatches messages"() {
        when:
        connection.dispatch("foo")

        then:
        1 * delegate.dispatch("foo")
        0 * onFailure.run()
    }

    def "receives messages"() {
        given:
        delegate.receive() >> "bar"

        when:
        def out = connection.receive()

        then:
        "bar" == out
        0 * onFailure.run()
    }

    def "treats failure to dispatch before receiving as a stale address"() {
        given:
        delegate.dispatch("foo") >> { throw new FooException() }

        when:
        connection.dispatch("foo")

        then:
        def ex = thrown(StaleDaemonAddressException)
        ex.cause instanceof FooException
        1 * onFailure.run()
        0 * onFailure._
    }

    def "handles failed dispatch"() {
        given:
        delegate.receive() >> "result"
        delegate.dispatch("broken") >> { throw new FooException() }

        when:
        connection.receive()
        connection.dispatch("broken")

        then:
        def ex = thrown(DaemonConnectionException)
        ex.class == DaemonConnectionException
        ex.cause instanceof FooException
        0 * onFailure._
    }

    def "treats failure to receive first message as a stale address"() {
        given:
        delegate.receive() >> { throw new FooException() }

        when:
        connection.receive()

        then:
        def ex = thrown(StaleDaemonAddressException)
        ex.cause instanceof FooException
        1 * onFailure.run()
        0 * onFailure._
    }

    def "handles failed receive"() {
        given:
        1 * delegate.receive() >> "first"
        delegate.receive() >> { throw new FooException() }

        when:
        connection.receive()
        connection.receive()

        then:
        def ex = thrown(DaemonConnectionException)
        ex.class == DaemonConnectionException
        ex.cause instanceof FooException
        0 * onFailure._
    }

    class FooException extends RuntimeException {}
}
