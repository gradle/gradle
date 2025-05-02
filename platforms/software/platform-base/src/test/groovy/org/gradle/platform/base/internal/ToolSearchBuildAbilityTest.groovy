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

package org.gradle.platform.base.internal

import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import spock.lang.Specification

class ToolSearchBuildAbilityTest extends Specification {
    ToolSearchResult result = Mock(ToolSearchResult)
    ToolSearchBuildAbility ability = new ToolSearchBuildAbility(result)

    def "is buildable when tool search is successful" () {
        when:
        result.isAvailable() >> true

        then:
        ability.isBuildable()
    }

    def "is not buildable when tool search is not successful" () {
        when:
        result.isAvailable() >> false

        then:
        !ability.isBuildable()
    }

    def "explains reason when tool search is not successful" () {
        def visitor = Mock(DiagnosticsVisitor)

        when:
        result.isAvailable() >> false
        result.explain(_) >> { DiagnosticsVisitor v ->
            v.node("Tool search failed")
        }
        ability.explain(visitor)

        then:
        1 * visitor.node("Tool search failed")
    }
}
