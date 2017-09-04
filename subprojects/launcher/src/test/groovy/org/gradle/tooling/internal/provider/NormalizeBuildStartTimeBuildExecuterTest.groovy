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

package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildRequestContext
import org.gradle.internal.time.Clock
import org.gradle.launcher.exec.BuildExecuter
import spock.lang.Specification

class NormalizeBuildStartTimeBuildExecuterTest extends Specification {

    def clock = Mock(Clock)
    def delegate = Mock(BuildExecuter)
    def executer = new NormalizeBuildStartTimeBuildExecuter(clock, delegate)

    def "does not alter request context if time is earlier than receive time"() {
        given:
        def ctx = context(10)
        clock.currentTime >> 20

        when:
        executer.execute(null, ctx, null, null)

        then:
        1 * delegate.execute(_, ctx, _, _)
    }

    def "sets request time to now if client time is later"() {
        given:
        def ctx = context(20)
        clock.currentTime >> 10

        when:
        executer.execute(null, ctx, null, null)

        then:
        1 * delegate.execute(_, { BuildRequestContext c -> c.startTime == 10 }, _, _)
    }

    BuildRequestContext context(long startTime) {
        Mock(BuildRequestContext) {
            getStartTime() >> startTime
        }
    }
}
