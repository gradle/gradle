/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.junit.Rule

class TestProgressDaemonErrorsCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    @Rule BlockingHttpServer server = new BlockingHttpServer()
    boolean killed = false

    void setup() {
        toolingApi.requireIsolatedDaemons()
    }

    def "should received failed event when daemon disappears unexpectedly with TAPI higher 2.4"() {
        given:
        goodCode()
        def sync = server.expectAndBlock('sync')

        when:
        def result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addProgressListener({ ProgressEvent event ->
                result << event
                if (!killed) {
                    sync.waitForAllPendingCalls()
                    sync.releaseAll()
                    toolingApi.daemons.daemon.kill()
                    killed = true
                }
            }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "build fails with a DaemonDisappearedException"
        GradleConnectionException ex = thrown()
        ex.cause.message.contains('Gradle build daemon disappeared unexpectedly')

        and:
        !result.empty
    }

    def goodCode() {
        server.start()
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { ${testImplementationConfiguration} 'junit:junit:4.13' }
            test.doLast {
                ${server.callFromBuild('sync')}
                Thread.sleep(120000)
            }
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }
}
