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

package org.gradle.integtests.fixtures.executer

import spock.lang.Specification

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.*

class TaskOrderSpecsTest extends Specification {
    def "can match order exactly (#executedTasks)"() {
        def spec = exact(':a', ':b', ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        noExceptionThrown()

        where:
        executedTasks << [
            [':a', ':b', ':c'],
            [':a', ':b', ':c', ':d'],
            [':z', ':a', ':b', ':c'],
            [':a', ':z', ':b', ':c'],
            [':z', ':a', ':x', ':y', ':b', ':c', ':d']
        ]
    }

    def "can mismatch order exactly (#executedTasks)"() {
        def spec = exact(':a', ':b', ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        thrown(AssertionError)

        where:
        executedTasks << [':a', ':b', ':c'].permutations().findAll { it != [':a', ':b', ':c'] } + [
            [':a', ':c'],
            [':a', ':b'],
            [':b', ':c'],
            [':a', ':c', ':b', ':d'],
            [':z', ':b', ':a', ':c'],
            [':z', ':c', ':x', ':y', ':b', ':a', ':d'],
            []
        ]
    }

    def "can match any order (#executedTasks)"() {
        def spec = any(':a', ':b', ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        noExceptionThrown()

        where:
        executedTasks << [':a', ':b', ':c'].permutations() + [
            [':c', ':b', ':a', ':d'],
            [':z', ':a', ':b', ':c'],
            [':a', ':z', ':c', ':b'],
            [':z', ':a', ':x', ':y', ':b', ':c', ':d']
        ]
    }

    def "can mismatch any order (#executedTasks)"() {
        def spec = any(':a', ':b', ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        thrown(AssertionError)

        where:
        executedTasks << [
            [':c', ':b', ':d'],
            [':z', ':a', ':b'],
            [':b', ':z', ':s'],
            [':z', ':a', ':x', ':y', ':b', ':d'],
            [ ':a' ],
            [ ':c' ],
            []
        ]
    }

    def "can match on 'any' rule nested inside 'exact' rule (#executedTasks)"() {
        def spec = exact(any(':a', ':b'), ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        noExceptionThrown()

        where:
        executedTasks << [
            [':a', ':b', ':c'],
            [':b', ':a', ':c'],
            [':a', ':z', ':b', ':c'],
            [':z', ':b', ':a', ':c'],
            [':b', ':z', ':a', ':x', ':c', ':d']
        ]
    }

    def "can mismatch on 'any' rule nested inside 'exact' rule (#executedTasks)"() {
        def spec = exact(any(':a', ':b'), ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        thrown(AssertionError)

        where:
        executedTasks << [
            [':a', ':b'],
            [':b', ':c'],
            [':a', ':c'],
            [':b', ':a', ':z'],
            [':c', ':a', ':b'],
            [':z', ':c', ':a', ':x'],
            [':c', ':z', ':a', ':x', ':b', ':d'],
            []
        ]
    }

    def "can match on 'exact' rule nested inside 'any' rule (#executedTasks)"() {
        def spec = any(exact(':a', ':b'), ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        noExceptionThrown()

        where:
        executedTasks << [
            [':a', ':b', ':c'],
            [':c', ':a', ':b'],
            [':a', ':z', ':b', ':c'],
            [':z', ':c', ':a', ':b'],
            [':a', ':z', ':b', ':x', ':c', ':d']
        ]
    }

    def "can mismatch on 'exact' rule nested inside 'any' rule (#executedTasks)"() {
        def spec = any(exact(':a', ':b'), ':c')

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        thrown(AssertionError)

        where:
        executedTasks << [
            [':a', ':b'],
            [':b', ':c'],
            [':a', ':c'],
            [':b', ':a', ':z'],
            [':c', ':b', ':a'],
            [':b', ':c', ':a'],
            [':b', ':a', ':c'],
            [':z', ':a', ':c', ':x'],
            [':c', ':z', ':b', ':x', ':a', ':d'],
            []
        ]
    }

    def "can match on complex rule (#executedTasks)"() {
        def spec = exact(any(':a', ':b'), any(':c', exact(':d', ':e', ':f')))

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        noExceptionThrown()

        where:
        executedTasks << [
            [':a', ':b', ':c', ':d', ':e', ':f'],
            [':b', ':a', ':c', ':d', ':e', ':f'],
            [':b', ':a', ':d', ':e', ':f', ':c'],
            [':a', ':b', ':d', ':e', ':f', ':c']
        ]
    }

    def "can mismatch on complex rule (#executedTasks)"() {
        def spec = exact(any(':a', ':b'), any(':c', exact(':d', ':e', ':f')))

        when:
        spec.assertMatches(-1, executedTasks)

        then:
        thrown(AssertionError)

        where:
        executedTasks << [
            [':a', ':b', ':c', ':e', ':d', ':f'],
            [':c', ':d', ':e', ':f', ':a', ':b'],
            [':z', ':a', ':d', ':e', ':f', ':c'],
            [':a', ':b', ':d', ':f', ':e', ':c'],
            []
        ]
    }

    def "can specify a task more than once in a complex rule"() {
        def spec = any(exact(':a', ':c'), exact(':b', ':d'), exact(':a', ':d'))

        given:
        assert spec.getTasks() == [':a', ':b', ':c', ':d'] as Set

        when:
        spec.assertMatches(-1, [':a', ':b', ':c', ':d'])

        then:
        noExceptionThrown()

        when:
        spec.assertMatches(-1, [':b', ':a', ':c', ':d'])

        then:
        noExceptionThrown()

        when:
        spec.assertMatches(-1, [':b', ':d', ':a', ':c'])

        then:
        thrown(AssertionError)
    }
}
