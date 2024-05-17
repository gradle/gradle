/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.server.api.HandleStop
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions

/**
 * Outlines the lifecycle of the daemon given different sequences of events.
 *
 * These tests are a little different due to their async nature. We use the classes
 * from the org.gradle.launcher.daemon.testing.* package to model a sequence of expected
 * daemon registry state changes, executing actions at certain state changes.
 */
class DaemonLifecycleSpec extends AbstractDaemonLifecycleSpec {

    def "daemons do some work - sit idle - then timeout and die"() {
        //in this particular test we need to make the daemon timeout
        //shorter than the state transition timeout so that
        //we can detect the daemon idling out within state verification window
        daemonIdleTimeout = stateTransitionTimeout / 2

        when:
        startBuild()

        then:
        busy()

        when:
        completeBuild()

        then:
        idle()

        and:
        stopped()
    }

    //Java 9 and above needs --add-opens to make environment variable mutation work
    @Requires(UnitTestPreconditions.Jdk8OrEarlier)
    def "existing foreground idle daemons are used"() {
        when:
        startForegroundDaemon()

        then:
        idle()

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
    }

    def "a new daemon is started if all existing are busy"() {
        when:
        startBuild()

        then:
        busy()

        when:
        startBuild()

        then:
        busy 2
    }

    def "sending stop to idle daemons causes them to terminate immediately"() {
        when:
        startBuild()

        then:
        busy()

        when:
        completeBuild()

        then:
        idle()

        when:
        stopDaemons()

        then:
        stopped()
    }

    def "stopping daemon that is building shows message"() {
        when:
        startBuild()

        then:
        waitForBuildToWait()

        when:
        stopDaemons()

        then:
        waitForLifecycleLogToContain(HandleStop.EXPIRATION_REASON)
    }

    def "daemon stops after current build if registry is deleted"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        when:
        waitForDaemonExpiration()

        then:
        completeBuild()

        and:
        stopped()
    }

    def "idle daemon stops immediately if registry is deleted"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()

        then:
        completeBuild()

        and:
        idle()

        when:
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        then:
        stopped()
    }

    def "daemon stops after current build if registry is deleted and recreated"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }
        startBuild()
        waitForBuildToWait(1)

        then:
        daemonContext(0) {
            assert (new DaemonDir(executer.daemonBaseDir).registry.exists())
        }

        when:
        waitForDaemonExpiration(0)

        then:
        completeBuild(0)
        completeBuild(1)

        and:
        idle 1
    }

    def "starting new build recreates registry and succeeds"() {
        File registry

        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        when:
        waitForDaemonExpiration()

        then:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        daemonContext {
            assert (registry.exists())
        }

        and:
        completeBuild()
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "if a daemon exists but is using a different java home, a new compatible daemon will be created and used"() {
        when:
        startForegroundDaemonWithAlternateJavaHome()

        then:
        idle()

        and:
        foregroundDaemonContext {
            assert javaHome.canonicalPath == AvailableJavaHomes.differentJdk.javaHome.canonicalPath
        }

        when:
        startBuild()

        then:
        numDaemons 2
        busy 1

        when:
        waitForBuildToWait()
        completeBuild()

        then:
        daemonContext {
            assert javaHome == Jvm.current().javaHome
        }
    }

    def "duplicate daemons expire quickly"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy 1

        when:
        startBuild()
        waitForBuildToWait(1)

        then:
        busy 2

        when:
        startBuild()
        waitForBuildToWait(2)

        then:
        busy 3

        when:
        completeBuild()

        then:
        state 2, 1

        when:
        completeBuild(1)
        completeBuild(2)

        then:
        state 0, 1
    }

    // GradleHandle.abort() does not work reliably on windows and creates flakiness
    @Requires(UnitTestPreconditions.NotWindows)
    def "daemon stops immediately if stop is requested and then client disconnects"() {
        when:
        startBuild()
        waitForBuildToWait()

        then:
        busy()
        // Cause the daemon to want to stop
        daemonContext {
            File registry = new DaemonDir(executer.daemonBaseDir).registry
            deleteFile(registry)
        }

        and:
        waitForDaemonExpiration()

        when:
        killBuild()

        then:
        stopped()
    }
}
