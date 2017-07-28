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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.junit.Rule

class TestProgressDaemonErrorsCrossVersionSpec extends ToolingApiSpecification {
    @Rule CyclicBarrierHttpServer server = new CyclicBarrierHttpServer()
    boolean killed = false

    void setup() {
        toolingApi.requireIsolatedDaemons()
    }

    @TargetGradleVersion(">=2.4")
    @ToolingApiVersion(">=2.5")
    def "should received failed event when daemon disappears unexpectedly with TAPI higher 2.4"() {
        given:
        goodCode()

        when:
        def result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addProgressListener({ ProgressEvent event ->
                result << event
                if (!killed) {
                    server.waitFor()
                    toolingApi.daemons.daemon.kill()
                    server.release()
                    killed = true
                }
            }, EnumSet.of(OperationType.TEST)).run()
        }

        then: "build fails with a DaemonDisappearedException"
        caughtGradleConnectionException = thrown()
        caughtGradleConnectionException.cause.message.contains('Gradle build daemon disappeared unexpectedly')

        and:
        !result.empty
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            test.doLast { new URL("$server.uri").text }
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
