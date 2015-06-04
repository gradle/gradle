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

package org.gradle.internal.session

import org.gradle.internal.concurrent.Stoppable
import spock.lang.Specification


class DefaultBuildSessionTest extends Specification {
    BuildSession buildSession = new DefaultBuildSession();

    def "stopping build session stops all stoppables" () {
        Stoppable stoppable1 = Mock(Stoppable)
        Stoppable stoppable2 = Mock(Stoppable)
        Stoppable stoppable3 = Mock(Stoppable)
        [ stoppable1, stoppable2, stoppable3 ].each { buildSession.add(it) }

        when:
        buildSession.stop()

        then:
        1 * stoppable1.stop()
        1 * stoppable2.stop()
        1 * stoppable3.stop()
    }

    def "a stoppable added to build session will be stopped after every session" () {
        Stoppable stoppable1 = Mock(Stoppable)
        buildSession.add(stoppable1)

        when:
        buildSession.stop()

        then:
        1 * stoppable1.stop()

        when:
        buildSession.stop()

        then:
        1 * stoppable1.stop()
    }
}
