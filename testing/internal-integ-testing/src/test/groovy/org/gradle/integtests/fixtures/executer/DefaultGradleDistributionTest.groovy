/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.util.GradleVersion
import spock.lang.Specification

/**
 * Tests {@link DefaultGradleDistribution}.
 */
class DefaultGradleDistributionTest extends Specification {

    def "reports correct version compatibility"() {
        def dist = new DefaultGradleDistribution(GradleVersion.version(version), null, null)

        expect:
        !dist.clientWorksWith(minClient - 1)
        dist.clientWorksWith(minClient)

        !dist.daemonWorksWith(minDaemon - 1)
        dist.daemonWorksWith(minDaemon)

        dist.clientWorksWith(max)
        !dist.clientWorksWith(max + 1)

        dist.daemonWorksWith(max)
        !dist.daemonWorksWith(max + 1)

        where:
        version   | minDaemon | minClient | max
        "4.0"     | 7         | 7         | 8
        "4.2.99"  | 7         | 7         | 8
        "4.3"     | 7         | 7         | 9
        "4.99"    | 7         | 7         | 10
        "5.0"     | 8         | 8         | 11
        "7.2.99"  | 8         | 8         | 16
        "7.3"     | 8         | 8         | 17
        "8.14.99" | 8         | 8         | 24
        "9.0"     | 17        | 8         | 24
    }

}
