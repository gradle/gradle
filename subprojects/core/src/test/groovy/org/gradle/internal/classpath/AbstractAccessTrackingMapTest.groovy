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

import spock.lang.Specification

import java.util.function.BiConsumer

abstract class AbstractAccessTrackingMapTest extends Specification {
    protected final Map<String, String> innerMap = ['existing': 'existingValue', 'other': 'otherValue']
    protected final BiConsumer<Object, Object> consumer = Mock()

    protected abstract Map<? super String, ? super String> getMapUnderTestToRead()

    def "get(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().get(key)

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | null            | null
    }

    def "getOrDefault(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().getOrDefault(key, 'defaultValue')

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | 'defaultValue'  | null
    }

    def "access to existing element with forEach() is tracked"() {
        when:
        def iterated = new HashMap<Object, Object>()
        getMapUnderTestToRead().forEach { k, v -> iterated.put(k, v) }

        then:
        iterated == innerMap
        1 * consumer.accept('existing', 'existingValue')
        1 * consumer.accept('other', 'otherValue')
        0 * consumer._
    }

    def "access to existing element with entrySet() is tracked"() {
        when:
        def result = new HashSet<>(getMapUnderTestToRead().entrySet())

        then:
        result == innerMap.entrySet()
        1 * consumer.accept('existing', 'existingValue')
        1 * consumer.accept('other', 'otherValue')
        0 * consumer._
    }
}
