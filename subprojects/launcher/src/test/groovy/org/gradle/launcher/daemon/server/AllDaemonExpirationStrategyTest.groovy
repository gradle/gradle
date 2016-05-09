/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.launcher.daemon.server

import com.google.common.collect.ImmutableList
import spock.lang.Specification

class AllDaemonExpirationStrategyTest extends Specification {
    private Daemon mockDaemon = Mock(Daemon)
    private DaemonExpirationStrategy c1;
    private DaemonExpirationStrategy c2;

    def setup() {
        c1 = Mock(DaemonExpirationStrategy)
        c2 = Mock(DaemonExpirationStrategy)
    }

    def "expires when all child strategies expire"() {
        given:
        AllDaemonExpirationStrategy agg = new AllDaemonExpirationStrategy(ImmutableList.of(c1, c2))

        when:
        1 * c1.checkExpiration(_) >> { new DaemonExpirationResult(true, true, "r1") }
        1 * c2.checkExpiration(_) >> { new DaemonExpirationResult(true, true, "r2") }

        then:
        DaemonExpirationResult result = agg.checkExpiration(mockDaemon)
        result.expired
        result.reason == "r1 and r2"
    }

    def "doesn't expire if a single child strategy doesn't expire"() {
        given:
        AllDaemonExpirationStrategy agg = new AllDaemonExpirationStrategy(ImmutableList.of(c1, c2))

        when:
        1 * c1.checkExpiration(_) >> { new DaemonExpirationResult(false, false, "r1") }
        0 * c2.checkExpiration(_)

        then:
        DaemonExpirationResult result = agg.checkExpiration(mockDaemon)
        !result.expired
        result.reason == null
    }

    def "doesn't expire if no strategies are passed"() {
        // Although there's an argument to be made for throwing an exception when it's used this way.

        when:
        AllDaemonExpirationStrategy agg = new AllDaemonExpirationStrategy(Collections.emptyList())

        then:
        DaemonExpirationResult result = agg.checkExpiration(mockDaemon)
        !result.expired
        result.reason == null
    }

    def "doesn't concatenate a null reason"() {
        given:
        AllDaemonExpirationStrategy agg = new AllDaemonExpirationStrategy(ImmutableList.of(c1, c2))

        when:
        1 * c1.checkExpiration(_) >> { new DaemonExpirationResult(true, true, null) }
        1 * c2.checkExpiration(_) >> { new DaemonExpirationResult(true, true, "r2") }

        then:
        DaemonExpirationResult result = agg.checkExpiration(mockDaemon)
        result.expired
        result.reason == "r2"
    }
}
