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
import spock.lang.Subject
import spock.util.concurrent.AsyncConditions

@Subject(BuildOperationIdentifierRegistry)
class BuildOperationIdentifierRegistryTest extends Specification {
    def "able to set and retrieve current operation across threads"() {
        given:
        def conditions = new AsyncConditions(3)

        when:
        def thread = Thread.start {
            // expect:
            conditions.evaluate { BuildOperationIdentifierRegistry.getCurrentOperationIdentifier() == null }

            // when:
            BuildOperationIdentifierRegistry.setCurrentOperationIdentifier(1L)

            // then:
            conditions.evaluate { BuildOperationIdentifierRegistry.getCurrentOperationIdentifier() == 1L }

            // when:
            BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier()

            // then:
            conditions.evaluate { BuildOperationIdentifierRegistry.getCurrentOperationIdentifier() == null }
        }

        then:
        conditions.await()

        cleanup:
        thread?.join(1000)
    }
}
