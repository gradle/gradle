/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.messaging.remote.internal.inet

import java.util.concurrent.atomic.AtomicInteger
import org.gradle.api.Action
import org.gradle.internal.id.UUIDGenerator
import org.gradle.messaging.remote.internal.DefaultMessageSerializer
import org.gradle.util.ConcurrentSpecification
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Timeout
import static java.util.Collections.synchronizedList

class TcpConnectorConcurrencyTest extends ConcurrentSpecification {

    final static LOGGER = LoggerFactory.getLogger(TcpConnectorConcurrencyTest)

    //sharing serializer adds extra flavor...
    final serializer = new DefaultMessageSerializer<Object>(getClass().classLoader)
    final outgoingConnector = new TcpOutgoingConnector()
    final incomingConnector = new TcpIncomingConnector(executorFactory, new InetAddressFactory(), new UUIDGenerator())

    @Timeout(60)
    @Ignore
    //TODO SF exposes concurrency issue
    def "can dispatch from multiple threads"() {
        def number = new AtomicInteger(1)
        def threads = 20
        def messages = synchronizedList([])

        Action action = new Action() {
            void execute(event) {
                while (true) {
                    def message = event.connection.receive()
                    LOGGER.debug("*** received: $message")
                    messages << message
                    if (messages.size() == threads || message == null) {
                        break;
                    }
                }
            }
        }

        def address = incomingConnector.accept(action, getClass().classLoader, false)
        def connection = outgoingConnector.connect(address, getClass().classLoader)

        when:
        def all = []
        threads.times {
            all << start {
                //exceptions carry lots of information so serialization/deserialization is slower
                //and hence better chance of reproducing the concurrency bugs
                def message = new RuntimeException("Message #" + number.getAndIncrement())
                connection.dispatch(message)
                LOGGER.debug("*** dispatched: $message")
            }
        }

        all*.completed()

        then:
        //let's give some time for the messages to arrive to the sink
        poll(20) {
            messages.size() == threads
            messages.each { it.toString().contains("Message #") }
        }

        cleanup:
        incomingConnector.requestStop()
    }
}
