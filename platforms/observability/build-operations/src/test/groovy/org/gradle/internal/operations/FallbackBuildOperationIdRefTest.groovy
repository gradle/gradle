/*
 * Copyright 2026 the original author or authors.
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

class FallbackBuildOperationIdRefTest extends Specification {

    def "returns #expected when preferred is #preferredId and fallback is #fallbackId"() {
        given:
        def ref = new FallbackBuildOperationIdRef(
            { preferredId } as BuildOperationIdRef,
            { fallbackId } as BuildOperationIdRef
        )

        expect:
        ref.getId() == expected

        where:
        preferredId                | fallbackId                 | expected
        new OperationIdentifier(1) | new OperationIdentifier(2) | new OperationIdentifier(1)
        new OperationIdentifier(1) | null                       | new OperationIdentifier(1)
        null                       | new OperationIdentifier(2) | new OperationIdentifier(2)
        null                       | null                       | null
    }
}
