/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState
import spock.lang.Specification
import spock.lang.Unroll

class ModuleSelectorsTest extends Specification {

    def selectors = new ModuleSelectors()

    def 'empty by default'() {
        expect:
        verifyEmpty(selectors)
    }

    def 'can add a selector not marked as deferring'() {
        given:
        def selector = Mock(ResolvableSelectorState)

        when:
        selectors.add(selector, false)

        then:
        selectors.size() == 1
        selectors.first() == selector
        selectors.iterator().next() == selector
        !selectors.checkDeferSelection()
    }

    def 'can remove a selector'() {
        given:
        def selector = Mock(ResolvableSelectorState)
        selectors.add(selector, false)

        when:
        def result = selectors.remove(selector)

        then:
        result
        verifyEmpty(selectors)
    }

    def 'can ad a selector marked as deferring'() {
        given:
        def selector = Mock(ResolvableSelectorState)

        when:
        selectors.add(selector, true)

        then:
        selectors.checkDeferSelection()
    }

    def 'clears deferring state on first access'() {
        given:
        def selector = Mock(ResolvableSelectorState)
        selectors.add(selector, true)

        when:
        selectors.checkDeferSelection()

        then:
        !selectors.checkDeferSelection()
    }

    def 'can add 2 selectors'() {
        given:
        def selector1 = Mock(ResolvableSelectorState)
        selectors.add(selector1, false)
        def selector2 = Mock(ResolvableSelectorState)

        when:
        selectors.add(selector2, false)

        then:
        selectors.size() == 2
        selectors.first() == selector1

        when:
        def iterator = selectors.iterator()

        then:
        iterator.next() == selector1
        iterator.next() == selector2
        !iterator.hasNext()
    }

    def 'when adding 2 selectors and one dynamic, non-dynamic is first'() {
        given:
        def selector1 = dynamicSelector()
        def selector2 = Mock(ResolvableSelectorState)

        when:
        selectors.add(selector1, false)
        selectors.add(selector2, false)

        then:
        selectors.size() == 2
        selectors.first() == selector2

        when:
        def iterator = selectors.iterator()

        then:
        iterator.next() == selector2
        iterator.next() == selector1
        !iterator.hasNext()
    }

    @Unroll
    def 'can add and remove selectors in any order'() {
        given:
        def selector1 =dynamicSelector()
        def selector2 = Mock(ResolvableSelectorState)
        def selector3 = dynamicSelector()
        def selector4 = Mock(ResolvableSelectorState)

        def candidateSelectors = [selector1, selector2, selector3, selector4]

        when:
        indexes.each {
            selectors.add(candidateSelectors[it], false)
        }
        indexes.each {
            selectors.remove(candidateSelectors[it])
        }

        then:
        verifyEmpty(selectors)

        where:
        indexes << [0, 1, 2, 3].permutations()

    }

    void verifyEmpty(ModuleSelectors<? extends ResolvableSelectorState> selectors) {
        assert selectors.size() == 0
        assert selectors.first() == null
        assert !selectors.iterator().hasNext()
        assert !selectors.checkDeferSelection()
    }

    ResolvableSelectorState dynamicSelector() {
        Mock(ResolvableSelectorState) {
            getVersionConstraint() >> Mock(ResolvedVersionConstraint) {
                isDynamic() >> true
            }
        }
    }
}
