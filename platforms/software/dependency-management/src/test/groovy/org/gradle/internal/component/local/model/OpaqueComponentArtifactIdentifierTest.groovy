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

package org.gradle.internal.component.local.model

import org.gradle.util.Matchers
import spock.lang.Specification

class OpaqueComponentArtifactIdentifierTest extends Specification {
    def "has useful string representation"() {
        expect:
        new OpaqueComponentArtifactIdentifier(new File("thing")).toString() == "thing"
    }

    def "identifiers are equal when display names are equal"() {
        def id = new OpaqueComponentArtifactIdentifier(new File("one"))
        def sameId = new OpaqueComponentArtifactIdentifier(new File("one"))
        def differentId = new OpaqueComponentArtifactIdentifier(new File("two"))

        expect:
        Matchers.strictlyEquals(id, sameId)
        id != differentId
    }
}
