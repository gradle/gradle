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

import org.gradle.util.GradleVersion
import spock.lang.Specification

class DistributionLocatorIntegrationTest extends Specification {
    def locator = new DistributionLocator()

    def "generates correct URI for release versions"() {
        expect:
        locator.getDistributionFor(GradleVersion.version("0.8")).toString() == "https://services.gradle.org/distributions/gradle-0.8-bin.zip"
        locator.getDistributionFor(GradleVersion.version("0.9.1")).toString() == "https://services.gradle.org/distributions/gradle-0.9.1-bin.zip"
        locator.getDistributionFor(GradleVersion.version("1.0-milestone-3")).toString() == "https://services.gradle.org/distributions/gradle-1.0-milestone-3-bin.zip"
        locator.getDistributionFor(GradleVersion.version("1.12")).toString() == "https://services.gradle.org/distributions/gradle-1.12-bin.zip"
        locator.getDistributionFor(GradleVersion.version("8.5")).toString() == "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
    }

    def "generates correct URI for snapshot versions"() {
        expect:
        locator.getDistributionFor(GradleVersion.version("8.5-20240101120000+0000")).toString() == "https://services.gradle.org/distributions-snapshots/gradle-8.5-20240101120000+0000-bin.zip"
        locator.getDistributionFor(GradleVersion.version("9.0-SNAPSHOT")).toString() == "https://services.gradle.org/distributions-snapshots/gradle-9.0-SNAPSHOT-bin.zip"
    }

    def "generates correct URI for different distribution types"() {
        expect:
        locator.getDistributionFor(GradleVersion.version("8.5"), "bin").toString() == "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
        locator.getDistributionFor(GradleVersion.version("8.5"), "all").toString() == "https://services.gradle.org/distributions/gradle-8.5-all.zip"
        locator.getDistributionFor(GradleVersion.version("8.5"), "src").toString() == "https://services.gradle.org/distributions/gradle-8.5-src.zip"
    }

    def "uses custom base URL from system property"() {
        given:
        def originalBaseUrl = System.getProperty("org.gradle.internal.services.base.url")
        System.setProperty("org.gradle.internal.services.base.url", "https://custom.example.com")

        expect:
        DistributionLocator.getBaseUrl() == "https://custom.example.com"
        locator.getDistributionFor(GradleVersion.version("8.5")).toString() == "https://custom.example.com/distributions/gradle-8.5-bin.zip"

        cleanup:
        if (originalBaseUrl != null) {
            System.setProperty("org.gradle.internal.services.base.url", originalBaseUrl)
        } else {
            System.clearProperty("org.gradle.internal.services.base.url")
        }
    }

    def "uses default base URL when system property is not set"() {
        given:
        def originalBaseUrl = System.getProperty("org.gradle.internal.services.base.url")
        System.clearProperty("org.gradle.internal.services.base.url")

        expect:
        DistributionLocator.getBaseUrl() == "https://services.gradle.org"

        cleanup:
        if (originalBaseUrl != null) {
            System.setProperty("org.gradle.internal.services.base.url", originalBaseUrl)
        }
    }
}
