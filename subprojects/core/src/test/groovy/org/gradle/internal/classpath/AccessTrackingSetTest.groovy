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

package org.gradle.internal.classpath

import com.google.common.collect.ImmutableSet
import spock.lang.Specification

import java.util.function.Consumer

class AccessTrackingSetTest extends Specification {
    private final Consumer<Object> consumer = Mock()
    private final Set<String> inner = ImmutableSet.of('existing', 'other')
    private final AccessTrackingSet<String> set = new AccessTrackingSet<>(inner, consumer)

    @SuppressWarnings('GrEqualsBetweenInconvertibleTypes')
    def "reading set contents is tracked"() {
        when:
        Set<String> iterated = new HashSet<>()
        for (String v : set) {
            iterated.add(v)
        }

        then:
        iterated == inner
        1 * consumer.accept('existing')
        1 * consumer.accept('other')
        0 * consumer._
    }

    def "contains of existing element is tracked"() {
        when:
        def result = set.contains('existing')

        then:
        result
        1 * consumer.accept('existing')
        0 * consumer._
    }

    def "contains of null is tracked"() {
        when:
        def result = set.contains(null)

        then:
        !result
        1 * consumer.accept(null)
        0 * consumer._
    }

    def "contains of missing element is tracked"() {
        when:
        def result = set.contains('missing')

        then:
        !result
        1 * consumer.accept('missing')
        0 * consumer._
    }

    def "contains of inconvertible element is tracked"() {
        when:
        def result = set.contains(123)

        then:
        !result
        1 * consumer.accept(123)
        0 * consumer._
    }
}
