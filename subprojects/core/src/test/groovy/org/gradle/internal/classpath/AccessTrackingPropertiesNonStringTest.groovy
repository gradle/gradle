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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import spock.lang.Specification

import java.util.function.BiConsumer

class AccessTrackingPropertiesNonStringTest extends Specification {
    private static final Integer existingKey = Integer.valueOf(1)
    private static final Integer existingValue = Integer.valueOf(2)
    private static final Integer otherValue = Integer.valueOf(3)
    private static final Integer missingKey = Integer.valueOf(4)

    private final Properties innerProperties = propertiesWithContent(ImmutableMap.of(
        existingKey, existingValue,
        'existing', 'existingStringValue'
    ))
    private final BiConsumer<Object, Object> consumer = Mock()

    protected Properties getMapUnderTestToRead() {
        return new AccessTrackingProperties(innerProperties, consumer)
    }

    def "get(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().get(key)

        then:
        result == expectedResult
        0 * consumer._

        where:
        key         | expectedResult
        existingKey | existingValue
        missingKey  | null
    }

    def "getOrDefault(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().getOrDefault(key, 'defaultValue')

        then:
        result == expectedResult
        0 * consumer._

        where:
        key         | expectedResult
        existingKey | existingValue
        missingKey  | 'defaultValue'
    }

    def "containsKey(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().containsKey(key)

        then:
        result == expectedResult
        0 * consumer._

        where:
        key         | expectedResult
        existingKey | true
        missingKey  | false
    }

    def "entrySet() enumeration is tracked for strings only"() {
        when:
        def result = new HashSet<>(getMapUnderTestToRead().entrySet())

        then:
        result == innerProperties.entrySet()
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "entrySet() contains(entry(#key, #requestedValue)) and containsAll(entry(#key, #requestedValue)) are not tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().entrySet().contains(entry(key, requestedValue))

        then:
        containsResult == expectedResult
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().entrySet().containsAll(Collections.singleton(entry(key, requestedValue)))

        then:
        containsAllResult == expectedResult
        0 * consumer._

        where:
        key         | requestedValue | expectedResult
        existingKey | existingValue  | true
        existingKey | otherValue     | false
        existingKey | null           | false
        missingKey  | existingValue  | false
    }

    def "entrySet() containsAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().entrySet().containsAll(Arrays.asList(
            entry(existingKey, existingValue),
            entry('existing', 'existingStringValue')))
        then:
        result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "keySet() enumeration is tracked for strings only"() {
        when:
        def result = new HashSet<>(getMapUnderTestToRead().keySet())

        then:
        result == innerProperties.keySet()
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "keySet() contains(#key) and containsAll(#key) are not tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().keySet().contains(key)

        then:
        containsResult == expectedResult
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().keySet().containsAll(Collections.singleton(key))

        then:
        containsAllResult == expectedResult
        0 * consumer._

        where:
        key         | expectedResult
        existingKey | true
        missingKey  | false
    }

    def "keySet() containsAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().keySet().containsAll(Arrays.asList(existingKey, 'existing'))
        then:
        result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    private static Properties propertiesWithContent(Map<Object, Object> contents) {
        Properties props = new Properties()
        props.putAll(contents)
        return props
    }

    private static Map.Entry<Object, Object> entry(Object a, Object b) {
        return Maps.immutableEntry(a, b)
    }
}
