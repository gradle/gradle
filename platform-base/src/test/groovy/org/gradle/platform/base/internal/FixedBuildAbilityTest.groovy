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
import spock.lang.Specification

class FixedBuildAbilityTest extends Specification {
    def "is buildable" () {
        when:
        def ability = new FixedBuildAbility(true)

        then:
        ability.buildable
    }

    def "is not buildable" () {
        when:
        def ability = new FixedBuildAbility(false)

        then:
        !ability.buildable
    }

    def "explains not buildable reason" () {
        def visitor = Mock(DiagnosticsVisitor)

        when:
        def ability = new FixedBuildAbility(false)
        ability.explain(visitor)

        then:
        1 * visitor.node("Disabled by user")
    }
}
