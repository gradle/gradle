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

package org.gradle.util.internal

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.internal.time.ExponentialBackoff
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import spock.lang.Specification

import static java.util.concurrent.TimeUnit.SECONDS

/**
 * Integration test for DistributionLocator that uses real network connections to verify that distributions can be located.
 * <p>
 * To avoid sporadic 503 responses from the server causing test failures, the test will retry using {@link ExponentialBackoff}
 * for up to {@link #RETRY_TIMEOUT_SECONDS} seconds.
 */
@Requires(UnitTestPreconditions.Online)
class DistributionLocatorIntegrationTest extends Specification {
    private static final int RETRY_TIMEOUT_SECONDS = 120
    private static final int CONNECTION_TIMEOUT_SECONDS = 5
    private static final int READ_TIMEOUT_SECONDS = 5

    private final locator = new DistributionLocator()
    private final distributions = new ReleasedVersionDistributions()
    private final queryRunner = ExponentialBackoff.of(RETRY_TIMEOUT_SECONDS, SECONDS, ExponentialBackoff.Signal.SLEEP)

    def "locates release versions"() {
        expect:
        urlExists(locator.getDistributionFor(GradleVersion.version("0.8")))
        urlExists(locator.getDistributionFor(GradleVersion.version("0.9.1")))
        urlExists(locator.getDistributionFor(GradleVersion.version("1.0-milestone-3")))
        urlExists(locator.getDistributionFor(GradleVersion.version("1.12")))
        urlExists(locator.getDistributionFor(distributions.mostRecentRelease.version))
    }

    /**
     * If this test fails, it means that the snapshot in `released-versions.json` is no longer available.
     * You need to update that entry with a recent snapshot by hand.
     */
    def "locates snapshot versions"() {
        expect:
        urlExists(locator.getDistributionFor(distributions.mostRecentReleaseSnapshot.version))
    }

    private void urlExists(URI url) {
        def query = new DistributionExistsQuery(url)
        def responseCode = queryRunner.retryUntil(query)
        assert responseCode == 200
    }

    private static final class DistributionExistsQuery implements ExponentialBackoff.Query<Integer> {
        private final URI url
        private int attempt = 0

        DistributionExistsQuery(URI url) {
            this.url = url
        }

        @Override
        ExponentialBackoff.Result<Integer> run() throws IOException, InterruptedException {
            attempt++
            int responseCode = attemptConnection(url)
            if (responseCode == 200) {
                return ExponentialBackoff.Result.successful(responseCode)
            } else {
                println "Attempt ${attempt}: Failed to connect to ${url}, response code: ${responseCode}"
                return ExponentialBackoff.Result.notSuccessful(responseCode)
            }
        }

        private int attemptConnection(URI url) {
            def connection = url.toURL().openConnection() as HttpURLConnection
            connection.setConnectTimeout(CONNECTION_TIMEOUT_SECONDS * 1000)
            connection.setReadTimeout(READ_TIMEOUT_SECONDS * 1000)
            connection.requestMethod = "HEAD"
            connection.connect()
            return connection.responseCode
        }
    }
}
