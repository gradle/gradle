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
import org.gradle.util.MockExecutor
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * by Szczepan Faber, created at: 2/6/12
 */
class DaemonStateCoordinatorTest extends Specification {
    final Runnable onStart = Mock(Runnable)
    final Runnable onStartCommand = Mock(Runnable)
    final Runnable onFinishCommand = Mock(Runnable)
    final Runnable onStopRequested = Mock(Runnable)
    final Runnable onStop = Mock(Runnable)
    final MockExecutor executor = new MockExecutor()
    def coordinator = new DaemonStateCoordinator(executor, onStart, onStartCommand, onFinishCommand, onStop, onStopRequested)

    def "runs actions on stop"() {
        given:
        coordinator.start()

        expect:
        !coordinator.stopped

        when: "stopped first time"
        coordinator.stop()

        then: "stops"
        coordinator.stopped
        1 * onStop.run()
        1 * onStopRequested.run()

        when: "requested again"
        coordinator.stop()

        then:
        coordinator.stopped
        0 * _._
    }

    def "await idle timeout throws exception when already stopped"() {
        given:
        coordinator.start()
        coordinator.stop()

        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.HOURS)

        then:
        DaemonStoppedException e = thrown()
    }
    def "await idle timeout waits for requested time and then stops"() {
        given:
        coordinator.start()

        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.MILLISECONDS)

        then:
        1 * onStopRequested.run()
        1 * onStop.run()
        0 * _._
    }

    def "cannot use coordinator when start has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        RuntimeException e = thrown()
        failure == e

        1 * onStart.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(Mock(Runnable), "operation")

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "await idle timeout returns immediately when start has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        RuntimeException e = thrown()
        failure == e

        1 * onStart.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.HOURS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "can stop when start has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        RuntimeException e = thrown()
        failure == e

        1 * onStart.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        1 * onStopRequested.run()
        1 * onStop.run()
        0 * _._
    }

    def "can request stop when start has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        RuntimeException e = thrown()
        failure == e

        1 * onStart.run() >> { throw failure }
        0 * _._

        when:
        coordinator.requestStop()

        then:
        1 * onStopRequested.run()
        0 * _._
    }

    def "requesting stop stops asynchronously"() {
        given:
        coordinator.start()

        expect:
        !coordinator.stopped

        when:
        coordinator.requestStop()

        then:
        coordinator.stoppingOrStopped
        !coordinator.stopped
        1 * onStopRequested.run()
        0 * _._

        when:
        executor.runNow()

        then:
        coordinator.stoppingOrStopped
        coordinator.stopped
        1 * onStop.run()
        0 * _._

        when:
        coordinator.requestStop()

        then:
        coordinator.stoppingOrStopped
        coordinator.stopped
        0 * _._
    }

    def "can stop when stop requested action has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        1 * onStart.run()
        0 * _._

        when:
        coordinator.stop()

        then:
        RuntimeException e = thrown()
        failure == e

        and:
        coordinator.stopped

        and:
        1 * onStopRequested.run() >> { throw failure }
        1 * onStop.run()
        0 * _._

        when:
        coordinator.stop()

        then:
        0 * _._
    }

    def "can stop when stop action has failed"() {
        def failure = new RuntimeException()

        when:
        coordinator.start()

        then:
        1 * onStart.run()
        0 * _._

        when:
        coordinator.stop()

        then:
        RuntimeException e = thrown()
        failure == e

        and:
        coordinator.stopped

        and:
        1 * onStopRequested.run()
        1 * onStop.run() >> { throw failure }
        0 * _._
    }

    def "runs actions when command is run"() {
        Runnable command = Mock()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run()
        0 * _._
    }

    def "runs actions when command fails"() {
        Runnable command = Mock()
        def failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

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
        coordinator.start()
        command.run() >> { coordinator.runCommand(Mock(Runnable), "other") }

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon is currently executing: command'
    }

    def "cannot run command after stop requested"() {
        Runnable command = Mock()

        given:
        coordinator.start()
        coordinator.requestStop()

        when:
        coordinator.runCommand(command, "command 2")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon is currently stopping.'
    }

    def "cannot run command after stopped"() {
        Runnable command = Mock()

        given:
        coordinator.start()
        coordinator.stopAsSoonAsIdle()

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException e = thrown()
        e.message == 'This daemon has stopped.'
    }

    def "cannot run command when start command action fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure

        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "await idle time returns immediately when start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.HOURS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "can stop when start command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stop()

        then:
        1 * onStopRequested.run()
        1 * onStop.run()
        0 * _._
    }

    def "cannot run command when finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure

        and:
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.runCommand(command, "command")

        then:
        DaemonUnavailableException unavailableException = thrown()
        unavailableException.message == 'This daemon is in a broken state and will stop.'
    }

    def "await idle time returns immediately when finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

        then:
        RuntimeException e = thrown()
        e == failure
        1 * onStartCommand.run()
        1 * command.run()
        1 * onFinishCommand.run() >> { throw failure }
        0 * _._

        when:
        coordinator.stopOnIdleTimeout(100, TimeUnit.HOURS)

        then:
        IllegalStateException illegalStateException = thrown()
        illegalStateException.message == 'This daemon is in a broken state.'
    }

    def "can stop when finish command action has failed"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "command")

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
        1 * onStopRequested.run()
        1 * onStop.run()
        0 * _._
    }

    def "stopAsSoonAsIdle stops immediately when idle"() {
        given:
        coordinator.start()

        expect:
        coordinator.idle

        when:
        coordinator.stopAsSoonAsIdle()

        then:
        coordinator.stopped
        coordinator.stoppingOrStopped

        and:
        1 * onStopRequested.run()
        1 * onStop.run()
    }

    def "stopAsSoonAsIdle stops once current command has completed"() {
        Runnable command = Mock()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "some command")

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.stopAsSoonAsIdle()
            assert !coordinator.stopped
            assert coordinator.stoppingOrStopped
        }

        and:
        coordinator.stopped

        and:
        1 * onStartCommand.run()
        1 * onStopRequested.run()

        and:
        1 * onStop.run()
        0 * _._
    }

    def "stopAsSoonAsIdle stops when command fails"() {
        Runnable command = Mock()
        RuntimeException failure = new RuntimeException()

        given:
        coordinator.start()

        when:
        coordinator.runCommand(command, "some command")

        then:
        1 * command.run() >> {
            assert coordinator.busy
            coordinator.stopAsSoonAsIdle()
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
        1 * onStopRequested.run()

        and:
        1 * onStop.run()
        0 * _._
    }
}
