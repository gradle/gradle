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

package org.gradle.integtests

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DistributionLocator
import spock.lang.Specification

@Requires(UnitTestPreconditions.Online)
class DistributionLocatorIntegrationTest extends Specification {
    private static final int CONNECTION_TIMEOUT_SECONDS = 60 * 1000
    private static final int READ_TIMEOUT_SECONDS = 60 * 1000
    def locator = new DistributionLocator()
    def distributions = new ReleasedVersionDistributions()

    def "locates release versions"() {
        expect:
        urlExist(locator.getDistributionFor(GradleVersion.version("0.8")))
        urlExist(locator.getDistributionFor(GradleVersion.version("0.9.1")))
        urlExist(locator.getDistributionFor(GradleVersion.version("1.0-milestone-3")))
        urlExist(locator.getDistributionFor(GradleVersion.version("1.12")))
    }

    /**
     * If this test fails, it means that the snapshot in `released-versions.json` is no longer available.
     * You need to update that entry with a recent snapshot by hand.
     */
    def "locates snapshot versions"() {
        expect:
        urlExist(locator.getDistributionFor(distributions.mostRecentReleaseSnapshot.version))
    }

    void urlExist(URI url) {
        HttpURLConnection connection = url.toURL().openConnection()
        connection.setConnectTimeout(CONNECTION_TIMEOUT_SECONDS)
        connection.setReadTimeout(READ_TIMEOUT_SECONDS)
        connection.requestMethod = "HEAD"
        connection.connect()
        assert connection.responseCode == 200
    }
}
