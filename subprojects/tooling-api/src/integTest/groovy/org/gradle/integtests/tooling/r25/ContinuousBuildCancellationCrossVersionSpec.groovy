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

import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.util.GradleVersion
import org.junit.Rule

class ContinuousBuildCancellationCrossVersionSpec extends ContinuousBuildToolingApiSpecification {

    @Rule
    CyclicBarrierHttpServer cyclicBarrierHttpServer = new CyclicBarrierHttpServer()

    def "client can cancel during execution of a continuous build"() {
        given:
        buildFile << """
            gradle.taskGraph.whenReady { new URL('${cyclicBarrierHttpServer.uri}').text }
        """

        when:
        runBuild {
            cyclicBarrierHttpServer.waitFor()
            cancel()
            cyclicBarrierHttpServer.release()
        }

        then:
        if (toolingApiVersion.equals(GradleVersion.version("2.1"))) {
            assert buildResult.failure instanceof GradleConnectionException
        } else {
            assert buildResult.failure instanceof BuildCancelledException
        }
        !stdout.toString().contains(WAITING_MESSAGE)
    }


    def "logging does not include message to use ctrl-d to exit"() {
        when:
        runBuild {
            succeeds()
            cancel()
        }

        then:
        !result.output.contains("ctrl-d")
        result.output.contains(WAITING_MESSAGE)
    }

    def "after cancelling a continuous build, we can subsequently run another"() {
        when:
        withConnection {
            runBuild {
                succeeds()
                cancel()
            }
            assert !buildResult.failure
            runBuild {
                succeeds()
                cancel()
            }
        }

        then:
        !buildResult.failure
    }

    def "can cancel in subsequent wait period"() {
        when:
        runBuild {
            succeeds()
            sourceDir.file("Thing.java") << "class Thing {}"
            succeeds()
            cancel()
        }

        then:
        !buildResult.failure
    }

}
