/*
 * Copyright 2014 the original author or authors.
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

class FormattingDiagnosticsVisitorTest extends Specification {
    def "formats candidates with examples"() {
        def visitor = new FormattingDiagnosticsVisitor()

        given:
        visitor.candidate("thing 1")
        visitor.candidate("thing 2").example("a")
        visitor.candidate("thing 3").example("a").example("b")

        expect:
        visitor.candidates == ["thing 1", "thing 2, for example a.", "thing 3, for example a, b."]
    }

    def "merges duplicate candidates"() {
        def visitor = new FormattingDiagnosticsVisitor()

        given:
        visitor.candidate("thing 1")
        visitor.candidate("thing 1").example("a")
        visitor.candidate("thing 1").example("b")

        expect:
        visitor.candidates == ["thing 1, for example a, b."]
    }
}
