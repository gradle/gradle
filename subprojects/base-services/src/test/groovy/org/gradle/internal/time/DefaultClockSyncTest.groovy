/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.time

import spock.lang.Specification

class DefaultClockSyncTest extends Specification {

    def timeSource = new ControlledTimeSource()
    def clockSync = new DefaultClockSync(timeSource)

    def "can force sync with wall clock"() {
        expect:
        clockSync.clock.currentTime == 0

        when:
        timeSource.nanoTime = 10

        then:
        clockSync.clock.currentTime == 10

        when:
        timeSource.currentTimeMillis = 20

        then:
        clockSync.clock.currentTime == 10

        when:
        timeSource.nanoTime = 9

        then:
        clockSync.clock.currentTime == 10

        when:
        clockSync.sync()

        then:
        clockSync.clock.currentTime == 20
    }

}
