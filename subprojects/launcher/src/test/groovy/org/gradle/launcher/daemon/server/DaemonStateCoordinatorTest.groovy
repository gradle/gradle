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
package org.gradle.launcher.daemon.server

import org.gradle.launcher.daemon.server.exec.DaemonUnavailableException
import spock.lang.Specification

import java.util.concurrent.TimeUnit
class DaemonStateCoordinatorTest extends Specification {
    final Runnable onStartCommand = Mock(Runnable)
    final Runnable onFinishCommand = Mock(Runnable)
    final Runnable onDisconnect = Mock(Runnable)
    final coordinator = new DaemonStateCoordinator(onStartCommand, onFinishCommand)

    def "can stop multiple times"() {
        expect:
        !coordinator.stopped

        when: "stopped first time"
        coordinator.stop()

        then: "stops"
        coordinator.stopped

        when: "requested again"
        coordinator.stop()

        then:
        coordinator.stopped
        0 * _._
    }

    def "await idle timeout throws exception when already stopped"() {
        given:
        coordinator.stop()

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        DaemonStoppedException e = thrown()
    }

    def "await idle timeout waits for specified time and then stops"() {
        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.MILLISECONDS)

        then:
        coordinator.stopped
        0 * _._
    }

    def "runs actions when command is run"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run()
        0 * _._
    }

    def "runs actions when command fails"() {
        Runnable command = Mock()
        def failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run() >> { throw failure }
        1 * onFinishCommand.run()
        0 * _._
    }

    def "cannot run command when another command is running"() {
        Runnable command = Mock()

        given:
        command.run() >> { coordinator.runCommand(Mock(Runnable), "other", Mock(Runnable)) }

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon is currently executing: command'
    }

    def "cannot run command after stop requested"() {
        Runnable command = Mock()

        given:
        coordinator.requestStop()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after forceful stop requested"() {
        Runnable command = Mock()

        given:
        coordinator.requestForcefulStop()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after stopped"() {
        Runnable command = Mock()

        given:
        coordinator.requestStop()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command after start command action fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure

        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "cannot run command after finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "await idle time returns immediately after start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "can stop when start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        0 * _._
    }

    def "can stop when finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        0 * _._
    }

    def "await idle time returns immediately after finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "requestStop stops immediately when idle"() {
        expect:
        coordinator.idle

        when:
        coordinator.requestStop()

        then:
        coordinator.stopped
        coordinator.stoppingOrStopped
    }

    def "requestStop stops once current command has completed"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "some command", onDisconnect)

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.requestStop()
            assert !coordinator.stopped
            assert coordinator.stoppingOrStopped
        }

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        0 * _._
    }

    def "requestStop stops when command fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "some command", onDisconnect)

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.requestStop()
            assert !coordinator.stopped
            assert coordinator.stoppingOrStopped
            throw failure
        }

        and:
        RuntimeException e = thrown()
        e == failure

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        0 * _._
    }

    def "await idle time returns after command has finished and stop requested"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "command", onDisconnect)
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        DaemonStoppedException e = thrown()

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            coordinator.requestStop()
        }
        0 * _._
    }

    def "requestForcefulStop stops immediately when idle"() {
        expect:
        !coordinator.stopped

        when:
        coordinator.requestForcefulStop()

        then:
        coordinator.stoppingOrStopped
        coordinator.stopped
        0 * _._
    }

    def "requestForcefulStop notifies disconnect handler and stops immediately when command running"() {
        Runnable command = Mock()

        expect:
        !coordinator.stopped

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        coordinator.stoppingOrStopped
        coordinator.stopped
        1 * onStartCommand.run()
        1 * command.run() >> {
            assert !coordinator.stopped
            coordinator.requestForcefulStop()
            assert coordinator.stopped
        }
        1 * onDisconnect.run()
        0 * _._
    }

    def "requestForcefulStop stops after disconnect action fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        when:
        coordinator.runCommand(command, "command", onDisconnect)

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            coordinator.requestForcefulStop()
        }
        1 * onDisconnect.run() >> {
            throw failure
        }
        0 * _._
    }

    def "await idle time returns immediately when forceful stop requested and command running"() {
        Runnable command = Mock()

        when:
        coordinator.runCommand(command, "command", onDisconnect)
        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)

        then:
        DaemonStoppedException e = thrown()

        and:
        1 * onStartCommand.run()
        1 * command.run() >> {
            coordinator.requestForcefulStop()
        }
        1 * onDisconnect.run()
        0 * _._
    }

}
