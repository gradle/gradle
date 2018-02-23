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

package org.gradle.internal.exceptions

import spock.lang.Specification

class ValueCollectingDiagnosticsVisitorTest extends Specification {
    def "collects union of potential values"() {
        def visitor = new ValueCollectingDiagnosticsVisitor()

        given:
        visitor.candidate("thing 1").values(["a", "b"])
        visitor.candidate("thing 2").values(["a", 1.2, false])
        visitor.candidate("thing 3")

        expect:
        visitor.values == ["a", "b", "false", "1.2"] as Set
    }
}
