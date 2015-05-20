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

package org.gradle.internal.filewatch

import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class IdleTimeoutTest extends Specification {

    def control = Mock(IdleTimeout.Control)
    def onTimeout = Mock(Runnable)
    def t = new IdleTimeout(control, onTimeout)
    def lastTimestamp

    def "can timeout"() {
        when:
        t.await()

        then:
        1 * control.await(-1) >> false

        and:
        1 * control.await(-1) >> {
            t.tick()
            false
        }

        and:
        1 * control.await(_) >> {
            lastTimestamp = it[0]
            t.tick()
            false
        }

        and:
        1 * control.await(_) >> {
            assert lastTimestamp < it[0]
            true
        }

        and:
        1 * onTimeout.run()
    }

    def "stop prevents fire"() {
        when:
        t.await()

        then:
        1 * control.await(-1) >> {
            t.stop()
            true
        }

        and:
        0 * control.await(_)
        0 * onTimeout.run()
    }

    def "timeout control"() {
        when:
        def control = new IdleTimeout.Timeout(50, { 200l })

        then:
        !control.await(-1)
        !control.await(190l)
        !control.await(151l)
        control.await(150l)
        control.await(149l)
        control.await(1l)
    }

    def "times out after inactivity"() {
        given:
        def timeout
        def t = new IdleTimeout(new IdleTimeout.Timeout(100), { timeout = true })

        when:
        Thread.start { t.await() }
        t.tick()

        then:
        new PollingConditions().eventually { timeout }
    }
}
