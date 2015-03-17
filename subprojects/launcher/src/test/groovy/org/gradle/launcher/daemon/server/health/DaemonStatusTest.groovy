/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health

import org.gradle.api.GradleException
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static org.gradle.launcher.daemon.server.health.DaemonStatus.EXPIRE_AT_PROPERTY

class DaemonStatusTest extends Specification {

    @Subject status = new DaemonStatus()
    def stats = Mock(DaemonStats)

    @Rule SetSystemProperties props = new SetSystemProperties()

    def "validates supplied threshold value"() {
        System.setProperty(EXPIRE_AT_PROPERTY, "foo")

        when:
        status.isDaemonTired(stats)

        then:
        def ex = thrown(GradleException)
        ex.message == "System property 'org.gradle.daemon.performance.expire-at' has incorrect value: 'foo'. The value needs to be integer."
    }

    @Unroll
    def "knows when daemon is tired"() {
        when:
        System.setProperty(EXPIRE_AT_PROPERTY, threshold.toString())
        stats.getCurrentPerformance() >> perf
        stats.getMemoryUsed() >> mem

        then:
        status.isDaemonTired(stats) == tired

        where:
        threshold | perf | mem   | tired
        90        | 89   | 100   | true
        90        | 90   | 90    | true
        90        | 91   | 100   | false
        0         | 0    | 100   | false
        0         | 1    | 100   | false
        100       | 100  | 100   | true
        100       | 100  | 60    | false
        75        | 80   | 0     | false
    }

    def "daemon hygiene is disabled by default"() {
        when:
        stats.getCurrentPerformance() >> 0
        stats.getMemoryUsed() >> 100

        then:
        !status.isDaemonTired(stats)
    }
}
