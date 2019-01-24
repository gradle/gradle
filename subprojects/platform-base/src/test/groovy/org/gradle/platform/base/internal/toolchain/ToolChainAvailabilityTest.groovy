/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.platform.base.internal.toolchain

import org.gradle.internal.logging.text.DiagnosticsVisitor
import spock.lang.Specification

class ToolChainAvailabilityTest extends Specification {
    def "visits message when unavailable"() {
        def visitor = Mock(DiagnosticsVisitor)

        given:
        def availability = new ToolChainAvailability()
        availability.unavailable("some reason")

        when:
        availability.explain(visitor)

        then:
        1 * visitor.node("some reason")
    }

    def "visits message when unsupported"() {
        def visitor = Mock(DiagnosticsVisitor)

        given:
        def availability = new ToolChainAvailability()
        availability.unsupported("some reason")

        when:
        availability.explain(visitor)

        then:
        1 * visitor.node("some reason")
    }

    def "ignores available tool"() {
        def searchResult = Mock(ToolSearchResult)

        given:
        searchResult.available >> true

        when:
        def availability = new ToolChainAvailability()
        availability.mustBeAvailable(searchResult)

        then:
        availability.available
    }

    def "visits missing tool"() {
        def visitor = Mock(DiagnosticsVisitor)
        def searchResult = Mock(ToolSearchResult)

        given:
        searchResult.available >> false

        and:
        def availability = new ToolChainAvailability()
        availability.mustBeAvailable(searchResult)

        when:
        availability.explain(visitor)

        then:
        1 * searchResult.explain(visitor)
    }
}
