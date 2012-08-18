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

import org.gradle.launcher.daemon.server.exec.DaemonBusyException

import java.util.concurrent.locks.Condition
import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 2/6/12
 */
class DaemonStateCoordinatorTest extends Specification {

    final Runnable onStart = Mock(Runnable)
    final Runnable onStartCommand = Mock(Runnable)
    final Runnable onFinishCommand = Mock(Runnable)
    final Runnable onStopRequested = Mock(Runnable)
    final Runnable onStop = Mock(Runnable)
    def coordinator = new DaemonStateCoordinator(onStart, onStartCommand, onFinishCommand, onStop, onStopRequested)

    def "requesting stop lifecycle"() {
        coordinator.asyncStop = Mock(Runnable)

        expect:
        !coordinator.stopped

        when: "requested first time"
        def passOne = coordinator.requestStop()

        then: "retruns true and schedules stopping"
        passOne == true
        1 * coordinator.asyncStop.run()
        1 * onStopRequested.run()
        coordinator.stoppingOrStopped
        !coordinator.stopped

        when: "requested again"
        def passTwo = coordinator.requestStop()

        then: "only returns false"
        passTwo == false
        0 * coordinator.asyncStop.run()
        0 * onStopRequested.run()
        coordinator.stoppingOrStopped
        !coordinator.stopped
    }

    def "stopping lifecycle"() {
        coordinator.condition = Mock(Condition)

        expect:
        !coordinator.stopped

        when: "stopped first time"
        coordinator.stop()

        then: "stops"
        1 * onStop.run()
        1 * onStopRequested.run()
        1 * coordinator.condition.signalAll()
        coordinator.stopped

        when: "requested again"
        coordinator.stop()

        then:
        0 * onStopRequested.run()
        1 * onStop.run()
        1 * coordinator.condition.signalAll()
        coordinator.stopped
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
        DaemonBusyException e = thrown()
        e.message == 'This daemon is currently executing: command'
    }

    def "stopAsSoonAsIdle when idle"() {
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

    def "stopAsSoonAsIdle when busy"() {
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
}
