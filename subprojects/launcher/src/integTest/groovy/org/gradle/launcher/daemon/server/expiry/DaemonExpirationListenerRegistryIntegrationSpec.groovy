/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.expiry

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Unroll

class DaemonExpirationListenerRegistryIntegrationSpec extends DaemonIntegrationSpec {

    @Unroll
    def "a daemon expiration listener receives expiration results continuous:#continuous"() {
        given:
        buildFile << """
           import org.gradle.launcher.daemon.server.*
           import org.gradle.launcher.daemon.server.expiry.*

           ${registerTestExpirationStrategy(50)}
           ${registerExpirationListener()}
           ${delayTask(200)}
        """

        when:
        def delayResult = executer.withArguments(continuous ? ['delay', '--continuous'] : ['delay']).run()

        then:
        delayResult.assertOutputContains('onExpirationEvent fired with: expiring daemon with TestExpirationStrategy')

        where:
        continuous << [true, false]

    }

    def "a daemon expiration listener receives expiration results when daemons run in the foreground"() {
        given:
        buildFile << """
           import org.gradle.launcher.daemon.server.*
           import org.gradle.launcher.daemon.server.expiry.*

           ${registerTestExpirationStrategy(50)}
           ${registerExpirationListener()}
           ${delayTask(200)}
        """

        when:
        startAForegroundDaemon()
        def delayResult = executer.withTasks('delay').run()

        then:
        delayResult.assertOutputContains("onExpirationEvent fired with: expiring daemon with TestExpirationStrategy")

    }

    static String delayTask(int sleep) {
        """
        task delay {
            doFirst{
             sleep(${sleep})
            }
        }
        """
    }

    static String registerExpirationListener() {
        """
        def registry = project.getServices().get(DaemonExpirationListenerRegistry)

        registry.register(new DaemonExpirationListener() {
            @Override
            public void onExpirationEvent(DaemonExpirationResult result) {
                println "onExpirationEvent fired with: \${result.getReason()}"
            }
        });
        """
    }

    static String registerTestExpirationStrategy(int frequency) {
        """
        import org.gradle.launcher.daemon.context.*

        class TestExpirationStrategy implements DaemonExpirationStrategy {
            Project project

            public TestExpirationStrategy(Project project){
                this.project = project
            }

            @Override
            public DaemonExpirationResult checkExpiration() {
                DaemonContext dc = null
                try {
                    dc = project.getServices().get(DaemonContext)
                } catch (Exception e) {
                    // ignore
                }
                return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "expiring daemon with TestExpirationStrategy uuid: \${dc?.getUid()}")
            }
        }

        def daemon =  project.getServices().get(Daemon)
        daemon.scheduleExpirationChecks(new AllDaemonExpirationStrategy([new TestExpirationStrategy(project)]), $frequency)
        """
    }


}
