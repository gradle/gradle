package org.gradle.launcher.daemon.server

import spock.lang.Specification

import java.util.concurrent.TimeUnit

class DaemonIdleTimeoutExpirationStrategyTest extends Specification {
    final Daemon daemon = Mock(Daemon)
    final DaemonStateCoordinator daemonStateCoordinator = Mock(DaemonStateCoordinator)

    def "daemon should expire when it's idle time exceeds idleTimeout"() {
        given:
        DaemonIdleTimeoutExpirationStrategy expirationStrategy = new DaemonIdleTimeoutExpirationStrategy(100L, TimeUnit.MILLISECONDS)

        when:
        1 * daemon.getStateCoordinator() >> { daemonStateCoordinator }
        1 * daemonStateCoordinator.getIdleMillis() >> { 101L }

        then:
        expirationStrategy.shouldExpire(daemon)
    }

    // TODO(ew): reimplement as integration test
//    def "await idle timeout does nothing when already stopped"() {
//        given:
//        coordinator.stop()
//
//        when:
//        coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)
//
//        then:
//        coordinator.stopped
//    }
//
//    def "await idle timeout waits for specified time and then stops"() {
//        when:
//        operation.waitForIdle {
//            coordinator.stopOnIdleTimeout(100, TimeUnit.MILLISECONDS)
//        }
//
//        then:
//        coordinator.stopped
//        operation.waitForIdle.duration in approx(100)
//
//        and:
//        0 * _._
//    }

//    def "await idle time returns after command has finished and stop requested"() {
//        def command = Mock(Runnable)
//
//        when:
//        start {
//            coordinator.runCommand(command, "command")
//        }
//        async {
//            thread.blockUntil.actionStarted
//            coordinator.requestStop()
//            coordinator.stopOnIdleTimeout(10000, TimeUnit.SECONDS)
//            instant.idle
//        }
//
//        then:
//        coordinator.stopped
//        instant.idle > instant.actionFinished
//
//        and:
//        1 * onStartCommand.run()
//        1 * command.run() >> {
//            instant.actionStarted
//            thread.block()
//            instant.actionFinished
//        }
//        0 * _._
//    }

    // TODO(ew): def "all daemons stop when their registry is deleted"() {}

    // TODO(ew): def "starting new build recreates registry"() {}
}
