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
package org.gradle.integtests.fixtures.executer

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.DistributionLocator

class ReleasedGradleDistribution extends DownloadableGradleDistribution {

    /**
     * When set (e.g. in CI behind a cluster HTTP cache), replaces {@link DistributionLocator#getBaseUrl()} in the
     * resolved distribution URI. Does not set {@code org.gradle.internal.services.base.url}, so Gradle under test is
     * unaffected.
     */
    private static final String INTERNAL_TEST_SERVICES_BASE_URL_ENV =
        "GRADLE_INTERNAL_TEST_SERVICES_BASE_URL"

    ReleasedGradleDistribution(String version, TestFile versionDir) {
        super(version, versionDir)
    }

    @Override
    protected URL getDownloadURL() {
        def uri = new DistributionLocator().getDistributionFor(getVersion())
        def baseOverride = System.getenv(INTERNAL_TEST_SERVICES_BASE_URL_ENV)?.trim()
        if (!baseOverride) {
            return uri.toURL()
        }
        def oldBase = DistributionLocator.getBaseUrl()
        def uriString = uri.toASCIIString()
        if (!uriString.startsWith(oldBase)) {
            throw new IllegalStateException("Expected distribution URI '$uriString' to start with '$oldBase'")
        }
        def newBase = baseOverride.replaceAll(/\/+$/, '')
        return new URL(newBase + uriString.substring(oldBase.length()))
    }
}
