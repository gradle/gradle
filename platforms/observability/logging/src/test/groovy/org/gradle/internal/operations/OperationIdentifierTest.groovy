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

class OperationIdentifierTest extends Specification {
    def "allows instantiation with non-zero values"() {
        expect:
        new OperationIdentifier(-1).getId() == -1
        new OperationIdentifier(1).getId() == 1
        new OperationIdentifier(Long.MAX_VALUE).getId() == Long.MAX_VALUE
        new OperationIdentifier(Long.MIN_VALUE).getId() == Long.MIN_VALUE
    }

    def "disallows instantiation with a value of 0"() {
        when:
        new OperationIdentifier(0)

        then:
        thrown(IllegalArgumentException)
    }
}
