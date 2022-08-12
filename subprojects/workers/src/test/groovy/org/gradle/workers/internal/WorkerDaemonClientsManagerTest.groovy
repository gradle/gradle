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

package org.gradle.workers.internal

import org.gradle.api.Transformer
import org.gradle.api.logging.LogLevel
import org.gradle.internal.session.BuildSessionLifecycleListener
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.service.scopes.Scopes
import org.gradle.process.internal.ExecException
import org.gradle.process.internal.health.memory.MBeanOsMemoryInfo
import org.gradle.process.internal.health.memory.MemoryManager
import org.gradle.util.ConcurrentSpecification
import spock.lang.Subject

class WorkerDaemonClientsManagerTest extends ConcurrentSpecification {
    def options = Stub(DaemonForkOptions)
    def starter = Stub(WorkerDaemonStarter)
    def listenerManager = Stub(ListenerManager)
    def loggingManager = Stub(LoggingManagerInternal)
    def memoryManager = Mock(MemoryManager)

    @Subject
        manager = new WorkerDaemonClientsManager(starter, listenerManager, loggingManager, memoryManager, new MBeanOsMemoryInfo())

    def "does not reserve idle client when no clients"() {
        expect:
        manager.reserveIdleClient(options) == null
    }

    def "does not reserve idle client when no matching client found"() {
        def noMatch = Stub(WorkerDaemonClient) {
            isCompatibleWith(_) >> false
        }

        expect:
        manager.reserveIdleClient(options, [noMatch]) == null
    }

    def "reserves idle client when match found"() {
        def noMatch = Stub(WorkerDaemonClient) { isCompatibleWith(_) >> false }
        def match = Stub(WorkerDaemonClient) { isCompatibleWith(_) >> true }
        def input = [noMatch, match]

        expect:
        manager.reserveIdleClient(options, input) == match
        input == [noMatch] //match removed from input
    }

    def "reserves new client"() {
        def newClient = Stub(WorkerDaemonClient)
        starter.startDaemon(options, _) >> newClient

        when:
        def client = manager.reserveNewClient(options)

        then:
        newClient == client
    }

    def "can stop all created clients"() {
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        manager.stop()

        then:
        1 * client1.stop()
        1 * client2.stop()
    }

    def "stops all other clients when a client fails to stop"() {
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        manager.stop()

        then:
        thrown(ExecException)
        1 * client1.stop() >> { throw new ExecException("FAILED!") }
        1 * client2.stop()
    }

    def "stopping a failed client removes the client"() {
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        manager.stop()

        then:
        thrown(ExecException)
        1 * client1.stop() >> { throw new ExecException("FAILED!") }
        1 * client2.stop()

        and:
        manager.allClients.size() == 0
    }

    def "exception contains all errors when multiple clients fail to stop"() {
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        manager.stop()

        then:
        def e = thrown(DefaultMultiCauseException)
        1 * client1.stop() >> { throw new ExecException("FAILED1!") }
        1 * client2.stop() >> { throw new ExecException("FAILED2!") }

        and:
        e.causes.size() == 2
        e.causes.collect { it.message }.sort() == ["FAILED1!", "FAILED2!"]
    }

    def "can stop session-scoped clients"() {
        listenerManager = new DefaultListenerManager(Scopes.BuildSession)
        manager = new WorkerDaemonClientsManager(starter, listenerManager, loggingManager, memoryManager, new MBeanOsMemoryInfo())
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()

        then:
        1 * client1.getKeepAliveMode() >> KeepAliveMode.SESSION
        1 * client2.getKeepAliveMode() >> KeepAliveMode.SESSION
        1 * client1.stop()
        1 * client2.stop()
    }

    def "Stopping session-scoped clients does not stop other clients"() {
        listenerManager = new DefaultListenerManager(Scopes.BuildSession)
        manager = new WorkerDaemonClientsManager(starter, listenerManager, loggingManager, memoryManager, new MBeanOsMemoryInfo())
        def client1 = Mock(WorkerDaemonClient)
        def client2 = Mock(WorkerDaemonClient)
        starter.startDaemon(options, _) >>> [client1, client2]

        when:
        manager.reserveNewClient(options)
        manager.reserveNewClient(options)
        listenerManager.getBroadcaster(BuildSessionLifecycleListener).beforeComplete()

        then:
        1 * client1.getKeepAliveMode() >> KeepAliveMode.SESSION
        1 * client2.getKeepAliveMode() >> KeepAliveMode.DAEMON
        1 * client1.stop()
        0 * client2.stop()
    }

    def "clients can be released for further use"() {
        def client = Mock(WorkerDaemonClient) {
            isCompatibleWith(_) >> true
            getLogLevel() >> LogLevel.DEBUG
        }
        starter.startDaemon(options, _) >> client

        when:
        manager.reserveNewClient(options)

        then:
        manager.reserveIdleClient(options) == null

        when:
        manager.release(client)

        then:
        manager.reserveIdleClient(options) == client
    }

    def "clients are discarded when log level changes"() {
        OutputEventListener listener
        def client = Mock(WorkerDaemonClient) {
            isCompatibleWith(_) >> true
            getLogLevel() >> LogLevel.INFO
        }
        starter.startDaemon(options, _) >> client
        loggingManager.addOutputEventListener(_) >> { args -> listener = args[0] }
        loggingManager.getLevel() >> LogLevel.INFO

        when:
        manager = new WorkerDaemonClientsManager(starter, listenerManager, loggingManager, memoryManager, new MBeanOsMemoryInfo())

        then:
        listener != null

        when:
        manager.reserveNewClient(options)

        then:
        manager.release(client)

        when:
        listener.onOutput(Stub(LogLevelChangeEvent) { getNewLogLevel() >> LogLevel.QUIET })
        def shouldBeNull = manager.reserveIdleClient(options)

        then:
        1 * client.stop()
        shouldBeNull == null
    }

    def "prefers to stop less frequently used idle clients when releasing memory"() {
        def client1 = Mock(WorkerDaemonClient) { _ * getUses() >> 5 }
        def client2 = Mock(WorkerDaemonClient) { _ * getUses() >> 1 }
        def client3 = Mock(WorkerDaemonClient) { _ * getUses() >> 3 }
        starter.startDaemon(options, _) >>> [client1, client2, client3]
        def stopMostPreferredClient = new Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>>() {
            @Override
            List<WorkerDaemonClient> transform(List<WorkerDaemonClient> workerDaemonClients) {
                return workerDaemonClients[0..0]
            }
        }

        when:
        3.times { manager.reserveNewClient(options) }
        [client1, client2, client3].each { manager.release(it) }
        manager.selectIdleClientsToStop(stopMostPreferredClient)

        then:
        1 * client2.stop()

        when:
        manager.selectIdleClientsToStop(stopMostPreferredClient)

        then:
        1 * client3.stop()

        and:
        0 * client1.stop()
    }

    def "does not stop busy clients when releasing memory"() {
        def client1 = Mock(WorkerDaemonClient) { _ * getUses() >> 5 }
        def client2 = Mock(WorkerDaemonClient) { _ * getUses() >> 1 }
        def client3 = Mock(WorkerDaemonClient) { _ * getUses() >> 3 }
        starter.startDaemon(options, _) >>> [client1, client2, client3]
        def stopAll = new Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>>() {
            @Override
            List<WorkerDaemonClient> transform(List<WorkerDaemonClient> workerDaemonClients) {
                return workerDaemonClients
            }
        }

        when:
        3.times { manager.reserveNewClient(options) }
        manager.release(client3)
        manager.selectIdleClientsToStop(stopAll)

        then:
        0 * client1.stop()
        0 * client2.stop()
        1 * client3.stop()
    }

    def "registers/deregisters a worker daemon expiration with the memory manager"() {
        WorkerDaemonExpiration workerDaemonExpiration

        when:
        def manager = new WorkerDaemonClientsManager(starter, listenerManager, loggingManager, memoryManager, new MBeanOsMemoryInfo())

        then:
        1 * memoryManager.addMemoryHolder(_) >> { args -> workerDaemonExpiration = args[0] }

        when:
        manager.stop()

        then:
        1 * memoryManager.removeMemoryHolder(_) >> { args -> assert args[0] == workerDaemonExpiration }
    }
}
