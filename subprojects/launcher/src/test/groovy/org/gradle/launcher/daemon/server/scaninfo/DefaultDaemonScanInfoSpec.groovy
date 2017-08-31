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

package org.gradle.launcher.daemon.server.scaninfo

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.time.DefaultEventTimer
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultDaemonScanInfoSpec extends ConcurrentSpec {
    def "should unregister both listeners on build finished"() {
        given:
        def listenerManager = Mock(ListenerManager)
        def notifyAction = Mock(Action)
        def daemonScanInfo = new DefaultDaemonScanInfo(Stub(DaemonRunningStats), 0, Stub(DaemonRegistry), listenerManager)
        DaemonExpirationListener daemonExpirationListener
        BuildListener buildListener

        when:
        daemonScanInfo.notifyOnUnhealthy(notifyAction)

        then:
        1 * listenerManager.addListener({ it instanceof DaemonExpirationListener }) >> { DaemonExpirationListener listener ->
            daemonExpirationListener = listener
        }
        1 * listenerManager.addListener({ it instanceof BuildListener }) >> { BuildListener listener ->
            buildListener = listener
        }

        when:
        buildListener.buildFinished(Stub(BuildResult))

        then:
        1 * listenerManager.removeListener(daemonExpirationListener)
        1 * listenerManager.removeListener(buildListener)
    }

    def "should unregister daemon expiration listener on notification"() {
        given:
        def listenerManager = Mock(ListenerManager)
        def notifyAction = Mock(Action)
        def daemonScanInfo = new DefaultDaemonScanInfo(Stub(DaemonRunningStats), 0, Stub(DaemonRegistry), listenerManager)
        DaemonExpirationListener daemonExpirationListener
        BuildListener buildListener

        when:
        daemonScanInfo.notifyOnUnhealthy(notifyAction)

        then:
        1 * listenerManager.addListener({ it instanceof DaemonExpirationListener }) >> { DaemonExpirationListener listener ->
            daemonExpirationListener = listener
        }
        1 * listenerManager.addListener({ it instanceof BuildListener }) >> { BuildListener listener ->
            buildListener = listener
        }

        when:
        daemonExpirationListener.onExpirationEvent(new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "reason"))

        then:
        1 * listenerManager.removeListener(daemonExpirationListener)
    }

    def "should not deadlock with deamon scan info"() {
        def manager = new DefaultListenerManager()
        def daemonScanInfo = new DefaultDaemonScanInfo(new DaemonRunningStats(new DefaultEventTimer()), 1000, Mock(DaemonRegistry), manager)
        daemonScanInfo.notifyOnUnhealthy {
            println "Hello"
            sleep 500
        }

        when:
        async {
            start {
                manager.getBroadcaster(DaemonExpirationListener).onExpirationEvent(new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "test"))
            }
            start {
                sleep 100
                manager.getBroadcaster(BuildListener).buildFinished(null)
            }
        }


        then:
        0 * _
    }
}
