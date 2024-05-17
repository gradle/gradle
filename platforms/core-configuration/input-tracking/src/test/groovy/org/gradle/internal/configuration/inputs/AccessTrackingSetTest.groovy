/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.configuration.inputs

import spock.lang.Specification

import java.util.function.Consumer

class AccessTrackingSetTest extends Specification {
    private final AccessTrackingSet.Listener listener = Mock()
    private final Set<String> inner = new HashSet<>(Arrays.asList('existing', 'other'))
    private final AccessTrackingSet<String> set = new AccessTrackingSet<>(inner, listener)

    def "contains of existing element is tracked"() {
        when:
        def result = set.contains('existing')

        then:
        result
        1 * listener.onAccess('existing')
        0 * listener._
    }

    def "contains of null is tracked"() {
        when:
        def result = set.contains(null)

        then:
        !result
        1 * listener.onAccess(null)
        0 * listener._
    }

    def "contains of missing element is tracked"() {
        when:
        def result = set.contains('missing')

        then:
        !result
        1 * listener.onAccess('missing')
        0 * listener._
    }

    def "contains of inconvertible element is tracked"() {
        when:
        def result = set.contains(123)

        then:
        !result
        1 * listener.onAccess(123)
        0 * listener._
    }

    def "containsAll of existing elements is tracked"() {
        when:
        def result = set.containsAll(Arrays.asList('existing', 'other'))

        then:
        result
        1 * listener.onAccess('existing')
        1 * listener.onAccess('other')
        0 * listener._
    }

    def "containsAll of missing elements is tracked"() {
        when:
        def result = set.containsAll(Arrays.asList('missing', 'alsoMissing'))

        then:
        !result
        1 * listener.onAccess('missing')
        1 * listener.onAccess('alsoMissing')
        0 * listener._
    }

    def "containsAll of missing and existing elements is tracked"() {
        when:
        def result = set.containsAll(Arrays.asList('missing', 'existing'))

        then:
        !result
        1 * listener.onAccess('missing')
        1 * listener.onAccess('existing')
        0 * listener._
    }

    def "remove of existing element is tracked"() {
        when:
        def result = set.remove('existing')

        then:
        result
        1 * listener.onAccess('existing')
        1 * listener.onRemove('existing')
        0 * listener._
    }

    def "remove of missing element is tracked"() {
        when:
        def result = set.remove('missing')

        then:
        !result
        1 * listener.onAccess('missing')
        then:
        1 * listener.onRemove('missing')
        then:
        0 * listener._
    }

    def "removeAll of existing elements is tracked"() {
        when:
        def result = set.removeAll(['existing', 'other'])

        then:
        result
        1 * listener.onAccess('existing')
        then:
        1 * listener.onRemove('existing')
        then:
        1 * listener.onAccess('other')
        then:
        1 * listener.onRemove('other')
        then:
        0 * listener._
    }

    def "removeAll of missing elements is tracked"() {
        when:
        def result = set.removeAll(['missing', 'alsoMissing'])

        then:
        !result
        1 * listener.onAccess('missing')
        then:
        1 * listener.onRemove('missing')
        then:
        1 * listener.onAccess('alsoMissing')
        then:
        1 * listener.onRemove('alsoMissing')
        then:
        0 * listener._
    }

    def "removeAll of existing and missing elements is tracked"() {
        when:
        def result = set.removeAll(['existing', 'missing'])

        then:
        result
        1 * listener.onAccess('existing')
        then:
        1 * listener.onRemove('existing')
        then:
        1 * listener.onAccess('missing')
        then:
        1 * listener.onRemove('missing')
        then:
        0 * listener._
    }

    def "method #methodName is reported as aggregating"() {
        when:
        operation.accept(set)

        then:
        (1.._) * listener.onAggregatingAccess()
        0 * listener._

        where:
        methodName            | operation
        "iterator()"          | call(s -> s.iterator())
        "toArray()"           | call(s -> s.toArray())
        "toArray()"           | call(s -> s.toArray())
        "toArray(T[])"        | call(s -> s.toArray(new String[0]))
        "forEach()"           | call(s -> s.forEach(e -> { }))
        "size()"              | call(s -> s.size())
        "isEmpty()"           | call(s -> s.isEmpty())
        "hashCode()"          | call(s -> s.hashCode())
        "equals(Object)"      | call(s -> Objects.equals(s, Collections.emptySet()))  // Ensure that a real equals is invoked and not a Groovy helper
        "removeIf(Predicate)" | call(s -> s.removeIf(e -> false))
    }

    def "method clear is reported"() {
        when:
        set.clear()

        then:
        inner.isEmpty()
        1 * listener.onClear()
        0 * listener._
    }

    def "method #methodName is not reported as aggregating"() {
        when:
        operation.accept(set)

        then:
        0 * listener._

        where:
        methodName    | operation
        "retainAll()" | call(s -> s.retainAll(Collections.emptySet()))
        "toString()"  | call(s -> s.toString())
    }

    // Shortcut to have a typed lambda expression in where: block
    static Consumer<Set<String>> call(Consumer<Set<String>> consumer) {
        return consumer
    }
}
