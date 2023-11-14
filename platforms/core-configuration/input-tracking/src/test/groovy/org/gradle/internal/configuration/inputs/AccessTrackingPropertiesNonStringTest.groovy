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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import spock.lang.Specification

import static org.gradle.internal.configuration.inputs.AccessTrackingPropertiesNonStringTest.TestData.EXISTING_KEY
import static org.gradle.internal.configuration.inputs.AccessTrackingPropertiesNonStringTest.TestData.EXISTING_VALUE
import static org.gradle.internal.configuration.inputs.AccessTrackingPropertiesNonStringTest.TestData.MISSING_KEY
import static org.gradle.internal.configuration.inputs.AccessTrackingPropertiesNonStringTest.TestData.NON_STRING_VALUE
import static org.gradle.internal.configuration.inputs.AccessTrackingPropertiesNonStringTest.TestData.OTHER_VALUE

class AccessTrackingPropertiesNonStringTest extends Specification {
    private enum TestData {
        EXISTING_KEY, EXISTING_VALUE, OTHER_VALUE, MISSING_KEY, NON_STRING_VALUE
    }

    private final Map<Object, Object> innerMap = ImmutableMap.of(
        EXISTING_KEY, EXISTING_VALUE,
        'existing', 'existingStringValue',
        'keyWithNonStringValue', NON_STRING_VALUE
    )
    private final AccessTrackingProperties.Listener listener = Mock()

    protected Properties getMapUnderTestToRead() {
        return getMapUnderTestToWrite()
    }

    protected Properties getMapUnderTestToWrite() {
        return getMapUnderTestToWrite(innerMap)
    }

    protected Properties getMapUnderTestToWrite(Map<Object, Object> contents) {
        return new AccessTrackingProperties(propertiesWithContent(contents), listener)
    }

    def "get(#key) is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().get(key)

        then:
        result == expectedResult
        1 * listener.onAccess(key, expectedResult)

        where:
        key                     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE
        MISSING_KEY             | null
        'keyWithNonStringValue' | NON_STRING_VALUE
    }

    def "getOrDefault(#key) is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().getOrDefault(key, 'defaultValue')

        then:
        result == expectedResult
        1 * listener.onAccess(key, trackedValue)

        where:
        key                     | trackedValue     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE   | EXISTING_VALUE
        MISSING_KEY             | null             | 'defaultValue'
        'keyWithNonStringValue' | NON_STRING_VALUE | NON_STRING_VALUE
    }

    def "containsKey(#key) is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().containsKey(key)

        then:
        result == expectedResult
        1 * listener.onAccess(key, trackedValue)

        where:
        key                     | trackedValue     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE   | true
        MISSING_KEY             | null             | false
        'keyWithNonStringValue' | NON_STRING_VALUE | true
    }

    def "getProperty(String) is tracked for non-string values"() {
        when:
        def result = getMapUnderTestToRead().getProperty('keyWithNonStringValue')

        then:
        result == null
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
    }

    def "getProperty(String, String) is tracked for non-string values"() {
        when:
        def result = getMapUnderTestToRead().getProperty('keyWithNonStringValue', 'defaultValue')

        then:
        result == 'defaultValue'
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
    }

    def "forEach is tracked for non-strings"() {
        when:
        HashMap<Object, Object> iterated = new HashMap<>()
        getMapUnderTestToRead().forEach(iterated::put)

        then:
        iterated.keySet() == innerMap.keySet()
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "entrySet() enumeration is tracked for non-strings"() {
        when:
        def result = ImmutableSet.copyOf(getMapUnderTestToRead().entrySet())

        then:
        result == innerMap.entrySet()
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "entrySet() contains(entry(#key, #requestedValue)) and containsAll(entry(#key, #requestedValue)) are tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().entrySet().contains(entry(key, requestedValue))

        then:
        containsResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        when:
        def containsAllResult = getMapUnderTestToRead().entrySet().containsAll(Collections.singleton(entry(key, requestedValue)))

        then:
        containsAllResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        where:
        key                     | expectedValue         | requestedValue   | expectedResult
        'existing'              | 'existingStringValue' | null             | false
        EXISTING_KEY            | EXISTING_VALUE        | EXISTING_VALUE   | true
        EXISTING_KEY            | EXISTING_VALUE        | OTHER_VALUE      | false
        EXISTING_KEY            | EXISTING_VALUE        | null             | false
        MISSING_KEY             | null                  | EXISTING_VALUE   | false
        'keyWithNonStringValue' | NON_STRING_VALUE      | NON_STRING_VALUE | true
        'keyWithNonStringValue' | NON_STRING_VALUE      | OTHER_VALUE      | false
        'keyWithNonStringValue' | NON_STRING_VALUE      | null             | false
    }

    def "entrySet() containsAll() is tracked for all entries"() {
        when:
        def result = getMapUnderTestToRead().entrySet().containsAll(Arrays.asList(
            entry(EXISTING_KEY, EXISTING_VALUE),
            entry('keyWithNonStringValue', NON_STRING_VALUE),
            entry('existing', 'existingStringValue')))
        then:
        result
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "keySet() enumeration is tracked for strings only"() {
        when:
        def result = ImmutableSet.copyOf(getMapUnderTestToRead().keySet())

        then:
        result == innerMap.keySet()
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "keySet() contains(#key) and containsAll(#key) are tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().keySet().contains(key)

        then:
        containsResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        when:
        def containsAllResult = getMapUnderTestToRead().keySet().containsAll(Collections.<Object> singleton(key))

        then:
        containsAllResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        where:
        key                     | expectedValue    | expectedResult
        EXISTING_KEY            | EXISTING_VALUE   | true
        MISSING_KEY             | null             | false
        'keyWithNonStringValue' | NON_STRING_VALUE | true
    }

    def "keySet() containsAll() is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToRead().keySet().containsAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        result
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "stringPropertyNames() contains(#key) and containsAll(#key) are tracked for non-strings"() {
        when:
        def containsResult = getMapUnderTestToRead().stringPropertyNames().contains(key)

        then:
        containsResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        when:
        def containsAllResult = getMapUnderTestToRead().stringPropertyNames().containsAll(Collections.<Object> singleton(key))

        then:
        containsAllResult == expectedResult
        1 * listener.onAccess(key, expectedValue)

        where:
        key                     | expectedValue    | expectedResult
        EXISTING_KEY            | EXISTING_VALUE   | false
        MISSING_KEY             | null             | false
        'keyWithNonStringValue' | NON_STRING_VALUE | false
    }

    def "stringPropertyNames() containsAll() is tracked for non-stringsy"() {
        when:
        def result = getMapUnderTestToRead().stringPropertyNames().containsAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        !result
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        0 * listener._
    }

    def "remove(#key) is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToWrite().remove(key)

        then:
        result == expectedResult
        1 * listener.onAccess(key, expectedResult)
        then:
        1 * listener.onRemove(key)
        then:
        0 * listener._

        where:
        key                     | expectedResult
        EXISTING_KEY            | EXISTING_VALUE
        MISSING_KEY             | null
        'keyWithNonStringValue' | NON_STRING_VALUE
    }

    def "keySet() remove(#key) and removeAll(#key) are tracked for non-strings"() {
        when:
        def removeResult = getMapUnderTestToWrite().keySet().remove(key)

        then:
        removeResult == expectedResult
        1 * listener.onAccess(key, expectedValue)
        then:
        1 * listener.onRemove(key)
        then:
        0 * listener._

        when:
        def removeAllResult = getMapUnderTestToWrite().keySet().removeAll(Collections.<Object> singleton(key))

        then:
        removeAllResult == expectedResult
        1 * listener.onAccess(key, expectedValue)
        then:
        1 * listener.onRemove(key)
        then:
        0 * listener._

        where:
        key                     | expectedValue    | expectedResult
        EXISTING_KEY            | EXISTING_VALUE   | true
        MISSING_KEY             | null             | false
        'keyWithNonStringValue' | NON_STRING_VALUE | true
    }

    def "keySet() removeAll() is tracked for non-strings"() {
        when:
        def result = getMapUnderTestToWrite().keySet().removeAll(Arrays.asList(EXISTING_KEY, 'keyWithNonStringValue', 'existing'))
        then:
        result
        1 * listener.onAccess(EXISTING_KEY, EXISTING_VALUE)
        1 * listener.onAccess('existing', 'existingStringValue')
        1 * listener.onAccess('keyWithNonStringValue', NON_STRING_VALUE)
        1 * listener.onRemove(EXISTING_KEY)
        1 * listener.onRemove('existing')
        1 * listener.onRemove('keyWithNonStringValue')
        0 * listener._
    }

    def "entrySet() remove(#key) and removeAll(#key) are tracked for non-strings"() {
        when:
        def removeResult = getMapUnderTestToWrite().entrySet().remove(entry(key, requestedValue))

        then:
        removeResult == expectedResult
        1 * listener.onAccess(key, expectedValue)
        1 * listener.onRemove(key)
        0 * listener._

        when:
        def removeAllResult = getMapUnderTestToWrite().entrySet().removeAll(Collections.singleton(entry(key, requestedValue)))

        then:
        removeAllResult == expectedResult
        1 * listener.onAccess(key, expectedValue)
        then:
        1 * listener.onRemove(key)
        then:
        0 * listener._

        where:
        key                     | expectedValue         | requestedValue   | expectedResult
        'existing'              | 'existingStringValue' | null             | false
        EXISTING_KEY            | EXISTING_VALUE        | EXISTING_VALUE   | true
        EXISTING_KEY            | EXISTING_VALUE        | OTHER_VALUE      | false
        EXISTING_KEY            | EXISTING_VALUE        | null             | false
        MISSING_KEY             | null                  | EXISTING_VALUE   | false
        'keyWithNonStringValue' | NON_STRING_VALUE      | NON_STRING_VALUE | true
        'keyWithNonStringValue' | NON_STRING_VALUE      | OTHER_VALUE      | false
        'keyWithNonStringValue' | NON_STRING_VALUE      | null             | false
    }

    def "replaceAll() uses equals for primitive wrappers and Strings"() {
        def map = getMapUnderTestToWrite(intValue: 100500, stringValue: "value")
        when:
        map.replaceAll { k, v ->
            switch (k) {
                case "intValue": return Integer.valueOf((int) v)
                case "stringValue": return new String((String) v)
            }
        }

        then:
        0 * listener.onChange(_, _)
    }

    def "replaceAll() uses reference equality for non-primitives"() {
        def value1 = [1]
        def value2 = [1]

        def map = getMapUnderTestToWrite(value: value1)
        when:
        map.replaceAll { k, v ->
            return value2
        }

        then:
        1 * listener.onChange("value", value2)
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
