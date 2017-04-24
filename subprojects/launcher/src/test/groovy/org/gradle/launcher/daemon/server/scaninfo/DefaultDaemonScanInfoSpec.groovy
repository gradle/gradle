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
import org.gradle.internal.event.ListenerManager
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import spock.lang.Specification
import spock.lang.Unroll

class DefaultDaemonScanInfoSpec extends Specification {
    @Unroll
    def "should unregister both listeners #scenario"() {
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
        switch (scenario) {
            case Scenario.ON_EXPIRATION:
                daemonExpirationListener.onExpirationEvent(new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "reason"))
                break
            case Scenario.ON_BUILD_FINISHED:
                buildListener.buildFinished(Stub(BuildResult))
                break
        }

        then:
        1 * listenerManager.removeListener(daemonExpirationListener)
        1 * listenerManager.removeListener(buildListener)

        where:
        scenario << [Scenario.ON_EXPIRATION, Scenario.ON_BUILD_FINISHED]
    }

    private enum Scenario {
        ON_EXPIRATION("on graceful expiration"), ON_BUILD_FINISHED("on build finished")
        String description

        Scenario(String description) {
            this.description = description
        }

        String toString() {
            description
        }
    }
}
