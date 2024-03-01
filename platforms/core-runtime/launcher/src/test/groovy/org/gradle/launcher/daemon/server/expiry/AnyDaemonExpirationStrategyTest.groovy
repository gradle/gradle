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


package org.gradle.launcher.daemon.server.expiry

import spock.lang.Specification

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.*

class AnyDaemonExpirationStrategyTest extends Specification {
    private DaemonExpirationStrategy c1;
    private DaemonExpirationStrategy c2;

    def setup() {
        c1 = Mock(DaemonExpirationStrategy)
        c2 = Mock(DaemonExpirationStrategy)
    }

    def "expires when any child strategy expires"() {
        given:
        AnyDaemonExpirationStrategy agg = new AnyDaemonExpirationStrategy([c1, c2])

        when:
        1 * c1.checkExpiration() >> { new DaemonExpirationResult(c1Status, "r1") }
        1 * c2.checkExpiration() >> { new DaemonExpirationResult(c2Status, "r2") }

        then:
        DaemonExpirationResult result = agg.checkExpiration()
        result.status == anyStatus
        result.reason == reason

        where:
        c1Status         | c2Status         | anyStatus        | reason
        QUIET_EXPIRE     | DO_NOT_EXPIRE    | QUIET_EXPIRE     | "r1"
        DO_NOT_EXPIRE    | GRACEFUL_EXPIRE  | GRACEFUL_EXPIRE  | "r2"
        QUIET_EXPIRE     | GRACEFUL_EXPIRE  | GRACEFUL_EXPIRE  | "r1 and r2"
        IMMEDIATE_EXPIRE | QUIET_EXPIRE     | IMMEDIATE_EXPIRE | "r1 and r2"
        DO_NOT_EXPIRE    | IMMEDIATE_EXPIRE | IMMEDIATE_EXPIRE | "r2"
        GRACEFUL_EXPIRE  | IMMEDIATE_EXPIRE | IMMEDIATE_EXPIRE | "r1 and r2"
    }

    def "doesn't expire if no child strategies expire"() {
        given:
        AnyDaemonExpirationStrategy agg = new AnyDaemonExpirationStrategy([c1, c2])

        when:
        1 * c1.checkExpiration() >> { new DaemonExpirationResult(DO_NOT_EXPIRE, null) }
        1 * c2.checkExpiration() >> { new DaemonExpirationResult(DO_NOT_EXPIRE, null) }

        then:
        DaemonExpirationResult result = agg.checkExpiration()
        result.status == DO_NOT_EXPIRE
        result.reason == null
    }

    def "doesn't expire if no strategies are passed"() {
        // Although there's an argument to be made for throwing an exception when it's used this way.

        when:
        AnyDaemonExpirationStrategy agg = new AnyDaemonExpirationStrategy(Collections.emptyList())

        then:
        DaemonExpirationResult result = agg.checkExpiration()
        result.status == DO_NOT_EXPIRE
        result.reason == null
    }
}
