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

package org.gradle.api.internal.project

import spock.lang.Specification

class ProjectPathSuggestionsTest extends Specification {

    def "formats candidates as message suffix"() {
        expect:
        ProjectPathSuggestions.formatSuggestions(":fooo", [":foo", ":fop", ":unrelated"]) == " Some candidates are: ':foo', ':fop'."
    }

    def "does not suggest the root project"() {
        expect:
        ProjectPathSuggestions.formatSuggestions(":b", [":", ":bb"]) == " Some candidates are: ':bb'."
    }

    def "formats nothing when there are no candidates"() {
        expect:
        ProjectPathSuggestions.formatSuggestions(":zzzzzz", [":foo"]) == ""
    }
}
