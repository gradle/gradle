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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.tooling.BuildCancelledException
import org.junit.Rule

class ContinuousBuildCancellationCrossVersionSpec extends ContinuousBuildToolingApiSpecification {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "client can cancel during execution of a continuous build"() {
        given:
        setupCancellationBuild()
        def sync = server.expectAndBlock('sync')

        when:
        runBuild {
            sync.waitForAllPendingCalls()
            cancellationTokenSource.cancel()
            sync.releaseAll()
        }

        then:
        assert buildResult.failure instanceof BuildCancelledException
        !stdout.toString().contains(waitingMessage)
    }

    private TestFile setupCancellationBuild() {
        server.start()
        buildFile << """
import org.gradle.initialization.BuildCancellationToken
import java.util.concurrent.CountDownLatch

def cancellationToken = services.get(BuildCancellationToken.class)
def latch = new CountDownLatch(1)

cancellationToken.addCallback {
    latch.countDown()
}

gradle.taskGraph.whenReady {
    ${server.callFromBuild('sync')}
    latch.await()
}
"""
    }

    def "logging does not include message to use ctrl-d to exit"() {
        when:
        runBuild {
            succeeds()
            cancel()
        }

        then:
        !result.output.contains("ctrl-d")
        result.output.contains(waitingMessage)
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
