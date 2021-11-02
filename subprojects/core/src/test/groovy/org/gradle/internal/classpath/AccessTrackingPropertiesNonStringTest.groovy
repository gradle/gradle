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
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import spock.lang.Specification

import java.util.function.BiConsumer

import static org.gradle.internal.classpath.AccessTrackingPropertiesNonStringTest.TestData.EXISTING_KEY
import static org.gradle.internal.classpath.AccessTrackingPropertiesNonStringTest.TestData.EXISTING_VALUE
import static org.gradle.internal.classpath.AccessTrackingPropertiesNonStringTest.TestData.MISSING_KEY
import static org.gradle.internal.classpath.AccessTrackingPropertiesNonStringTest.TestData.NON_STRING_VALUE
import static org.gradle.internal.classpath.AccessTrackingPropertiesNonStringTest.TestData.OTHER_VALUE

class AccessTrackingPropertiesNonStringTest extends Specification {
    private enum TestData {
        EXISTING_KEY, EXISTING_VALUE, OTHER_VALUE, MISSING_KEY, NON_STRING_VALUE
    }

    private final Map<Object, Object> innerMap = ImmutableMap.of(
        EXISTING_KEY, EXISTING_VALUE,
        'existing', 'existingStringValue',
        'keyWithNonStringValue', NON_STRING_VALUE
    )
    private final BiConsumer<Object, Object> consumer = Mock()

    protected Properties getMapUnderTestToRead() {
        return getMapUnderTestToWrite()
    }

    protected Properties getMapUnderTestToWrite() {
        return new AccessTrackingProperties(propertiesWithContent(innerMap), consumer)
    }

    def "get(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().get(key)

        then:
        result == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE
        MISSING_KEY             | null
        'keyWithNonStringValue' | NON_STRING_VALUE
    }

    def "getOrDefault(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().getOrDefault(key, 'defaultValue')

        then:
        result == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE
        MISSING_KEY             | 'defaultValue'
        'keyWithNonStringValue' | NON_STRING_VALUE
    }

    def "containsKey(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().containsKey(key)

        then:
        result == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | true
        MISSING_KEY             | false
        'keyWithNonStringValue' | true
    }

    def "getProperty(String) is not tracked for non-string values"() {
        when:
        def result = getMapUnderTestToRead().getProperty('keyWithNonStringValue')

        then:
        result == null
        0 * consumer._
    }

    def "getProperty(String, String) is not tracked for non-string values"() {
        when:
        def result = getMapUnderTestToRead().getProperty('keyWithNonStringValue', 'defaultValue')

        then:
        result == 'defaultValue'
        0 * consumer._
    }

    def "forEach is tracked for strings only"() {
        when:
        HashMap<Object, Object> iterated = new HashMap<>()
        getMapUnderTestToRead().forEach(iterated::put)

        then:
        iterated.keySet() == innerMap.keySet()
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "entrySet() enumeration is tracked for strings only"() {
        when:
        def result = ImmutableSet.copyOf(getMapUnderTestToRead().entrySet())

        then:
        result == innerMap.entrySet()
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
        key                     | requestedValue   | expectedResult
        'existing'              | null             | false
        EXISTING_KEY            | EXISTING_VALUE   | true
        EXISTING_KEY            | OTHER_VALUE      | false
        EXISTING_KEY            | null             | false
        MISSING_KEY             | EXISTING_VALUE   | false
        'keyWithNonStringValue' | NON_STRING_VALUE | true
        'keyWithNonStringValue' | OTHER_VALUE      | false
        'keyWithNonStringValue' | null             | false
    }

    def "entrySet() containsAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().entrySet().containsAll(Arrays.asList(
            entry(EXISTING_KEY, EXISTING_VALUE),
            entry('keyWithNonStringValue', NON_STRING_VALUE),
            entry('existing', 'existingStringValue')))
        then:
        result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "keySet() enumeration is tracked for strings only"() {
        when:
        def result = ImmutableSet.copyOf(getMapUnderTestToRead().keySet())

        then:
        result == innerMap.keySet()
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
        def containsAllResult = getMapUnderTestToRead().keySet().containsAll(Collections.<Object> singleton(key))

        then:
        containsAllResult == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | true
        MISSING_KEY             | false
        'keyWithNonStringValue' | true
    }

    def "keySet() containsAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().keySet().containsAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "stringPropertyNames() contains(#key) and containsAll(#key) are not tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().stringPropertyNames().contains(key)

        then:
        containsResult == expectedResult
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().stringPropertyNames().containsAll(Collections.<Object> singleton(key))

        then:
        containsAllResult == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | false
        MISSING_KEY             | false
        'keyWithNonStringValue' | false
    }

    def "stringPropertyNames() containsAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().stringPropertyNames().containsAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        !result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "remove(#key) is not tracked for non-strings"() {
        when:
        def result = getMapUnderTestToWrite().remove(key)

        then:
        result == expectedResult
        0 * consumer._
        where:
        key                     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE
        MISSING_KEY             | null
        'keyWithNonStringValue' | NON_STRING_VALUE
    }

    def "keySet() remove(#key) and removeAll(#key) are not tracked for non-strings"() {
        when:
        def removeResult = getMapUnderTestToWrite().keySet().remove(key)

        then:
        removeResult == expectedResult
        0 * consumer._

        when:
        def removeAllResult = getMapUnderTestToWrite().keySet().removeAll(Collections.<Object> singleton(key))

        then:
        removeAllResult == expectedResult
        0 * consumer._

        where:
        key                     | expectedResult
        EXISTING_KEY            | true
        MISSING_KEY             | false
        'keyWithNonStringValue' | true
    }

    def "keySet() removeAll() is tracked for strings only"() {
        when:
        def result = getMapUnderTestToRead().keySet().removeAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        result
        1 * consumer.accept('existing', 'existingStringValue')
        0 * consumer._
    }

    def "entrySet() remove(#key) and removeAll(#key) are not tracked for non-strings"() {
        when:
        def removeResult = getMapUnderTestToWrite().entrySet().remove(entry(key, requestedValue))

        then:
        removeResult == expectedResult
        0 * consumer._

        when:
        def removeAllResult = getMapUnderTestToWrite().entrySet().removeAll(Collections.singleton(entry(key, requestedValue)))

        then:
        removeAllResult == expectedResult
        0 * consumer._

        where:
        key                     | requestedValue   | expectedResult
        'existing'              | null             | false
        EXISTING_KEY            | EXISTING_VALUE   | true
        EXISTING_KEY            | OTHER_VALUE      | false
        EXISTING_KEY            | null             | false
        MISSING_KEY             | EXISTING_VALUE   | false
        'keyWithNonStringValue' | NON_STRING_VALUE | true
        'keyWithNonStringValue' | OTHER_VALUE      | false
        'keyWithNonStringValue' | null             | false
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
