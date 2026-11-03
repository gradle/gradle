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

package org.gradle.internal.operations

import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

class CurrentBuildOperationRefTest extends Specification {

    def "able to set and retrieve current operation across threads"() {
        given:
        def ref = new CurrentBuildOperationRef()
        def conditions = new AsyncConditions(3)

        def op = new DefaultBuildOperationRef(new OperationIdentifier(1), new OperationIdentifier(2))

        when:
        def thread = Thread.start {
            // expect:
            conditions.evaluate { ref.get() == null }

            // when:
            ref.set(op)

            // then:
            conditions.evaluate { ref.get().is(op) }

            // when:
            ref.clear()

            // then:
            conditions.evaluate { ref.get() == null }
        }

        then:
        conditions.await()

        cleanup:
        thread?.join(1000)
    }
}
