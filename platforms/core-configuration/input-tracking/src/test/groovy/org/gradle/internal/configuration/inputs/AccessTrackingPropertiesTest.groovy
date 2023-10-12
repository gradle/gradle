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

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams

import javax.annotation.Nullable
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer

class AccessTrackingPropertiesTest extends AbstractAccessTrackingMapTest {
    private BiConsumer<Object, Object> onChange = Mock()
    private Consumer<Object> onRemove = Mock()
    private Runnable onClear = Mock()

    private AccessTrackingProperties.Listener listener = new AccessTrackingProperties.Listener() {
        @Override
        void onAccess(Object key, @Nullable Object value) {
            onAccess.accept(key, value)
        }

        @Override
        void onChange(Object key, @Nullable Object newValue) {
            onChange.accept(key, newValue)
        }

        @Override
        void onRemove(Object key) {
            onRemove.accept(key)
        }

        @Override
        void onClear() {
            onClear.run()
        }
    }

    @Override
    protected Properties getMapUnderTestToRead() {
        return getMapUnderTestToWrite()
    }

    protected Properties getMapUnderTestToWrite() {
        return new AccessTrackingProperties(propertiesWithContent(innerMap), listener)
    }

    def "getProperty(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().getProperty(key)

        then:
        result == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | null            | null
    }

    def "getProperty(#key, 'defaultValue') is tracked"() {
        when:
        def result = getMapUnderTestToRead().getProperty(key, 'defaultValue')

        then:
        result == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

        where:
        key        | expectedResult  | reportedValue
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | 'defaultValue'  | null
    }

    def "stringPropertyNames() contains(#key) and containsAll(#key) is tracked"() {
        when:
        def containsResult = getMapUnderTestToRead().stringPropertyNames().contains(key)

        then:
        containsResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

        when:
        def containsAllResult = getMapUnderTestToRead().stringPropertyNames().containsAll(Collections.singleton(key))

        then:
        containsAllResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

        where:
        key        | expectedResult | reportedValue
        'existing' | true           | 'existingValue'
        'missing'  | false          | null
    }

    def "stringPropertyNames() containsAll(#key1, #key2) is tracked"() {
        when:
        def result = getMapUnderTestToRead().stringPropertyNames().containsAll(Arrays.asList(key1, key2))

        then:
        result == expectedResult
        1 * onAccess.accept(key1, reportedValue1)
        1 * onAccess.accept(key2, reportedValue2)
        0 * onAccess._

        where:
        key1       | reportedValue1  | key2           | reportedValue2 | expectedResult
        'existing' | 'existingValue' | 'other'        | 'otherValue'   | true
        'existing' | 'existingValue' | 'missing'      | null           | false
        'missing'  | null            | 'otherMissing' | null           | false
    }

    def "remove(#key) is tracked"() {
        when:
        def result = getMapUnderTestToWrite().remove(key)

        then:
        result == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        1 * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        where:
        key        | reportedValue   | expectedResult
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | null            | null
    }

    def "remove(#key, #removedValue) is tracked"() {
        when:
        def result = getMapUnderTestToWrite().remove(key, removedValue)

        then:
        result == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        expectedReportedRemovalsCount * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        where:
        key        | removedValue    | reportedValue   | expectedResult
        'existing' | 'existingValue' | 'existingValue' | true
        'existing' | 'missingValue'  | 'existingValue' | false
        'missing'  | 'missingValue'  | null            | false

        // onRemove is only called if the value is actually removed
        expectedReportedRemovalsCount = expectedResult ? 1 : 0
    }

    def "keySet() remove(#key) and removeAll(#key) are tracked"() {
        when:
        def removeResult = getMapUnderTestToWrite().keySet().remove(key)

        then:
        removeResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        1 * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        when:
        def removeAllResult = getMapUnderTestToWrite().keySet().removeAll(Collections.singleton(key))

        then:
        removeAllResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        1 * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        where:
        key        | reportedValue   | expectedResult
        'existing' | 'existingValue' | true
        'missing'  | null            | false
    }

    def "entrySet() remove(#key) and removeAll(#key) are tracked"() {
        when:
        def removeResult = getMapUnderTestToWrite().entrySet().remove(entry(key, requestedValue))

        then:
        removeResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        1 * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        when:
        def removeAllResult = getMapUnderTestToWrite().entrySet().removeAll(Collections.singleton(entry(key, requestedValue)))

        then:
        removeAllResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        then:
        1 * onRemove.accept(key)
        then:
        0 * onAccess._
        0 * onRemove._

        where:
        key        | requestedValue  | reportedValue   | expectedResult
        'existing' | 'existingValue' | 'existingValue' | true
        'existing' | 'someValue'     | 'existingValue' | false
        'missing'  | 'someValue'     | null            | false
    }

    def "aggregating method #methodName reports all properties as inputs"() {
        when:
        operation.accept(getMapUnderTestToRead())

        then:
        (1.._) * onAccess.accept('existing', 'existingValue')
        (1.._) * onAccess.accept('other', 'otherValue')
        0 * onAccess._

        where:
        methodName                                | operation
        "propertyNames()"                         | call(p -> p.propertyNames())
        "keys()"                                  | call(p -> p.keys())
        "elements()"                              | call(p -> p.elements())
        "replaceAll(BiFunction)"                  | call(p -> p.replaceAll((k, v) -> v))
        "save(OutputStream, String)"              | call(p -> p.save(ByteStreams.nullOutputStream(), ""))
        "store(OutputStream, String)"             | call(p -> p.store(ByteStreams.nullOutputStream(), ""))
        "store(Writer, String)"                   | call(p -> p.store(CharStreams.nullWriter(), ""))
        "storeToXML(OutputSteam, String)"         | call(p -> p.storeToXML(ByteStreams.nullOutputStream(), ""))
        "storeToXML(OutputSteam, String, String)" | call(p -> p.storeToXML(ByteStreams.nullOutputStream(), "", "UTF-8"))
        "list(PrintStream)"                       | call(p -> p.list(new PrintStream(ByteStreams.nullOutputStream())))
        "list(PrintWriter)"                       | call(p -> p.list(new PrintWriter(ByteStreams.nullOutputStream())))
        "equals(Object)"                          | call(p -> Objects.equals(p, new Properties()))  // Ensure that a real equals is invoked and not a Groovy helper
        "stringPropertyNames().iterator()"        | call(p -> p.stringPropertyNames().iterator())
        "stringPropertyNames().size()"            | call(p -> p.stringPropertyNames().size())
    }

    def "method #methodName does not report properties as inputs"() {
        when:
        operation.accept(getMapUnderTestToWrite())

        then:
        0 * onAccess._

        where:
        methodName          | operation
        "toString()"        | call(p -> p.toString())
        "clear()"           | call(p -> p.clear())
        "load(Reader)"      | call(p -> p.load(new StringReader("")))
        "load(InputStream)" | call(p -> p.load(new ByteArrayInputStream(new byte[0])))
    }

    def "method put(#key, #newValue) reports argument as input and change"() {
        when:
        def result = getMapUnderTestToWrite().put(key, newValue)

        then:
        result == oldValue
        1 * onAccess.accept(key, oldValue)
        then:
        1 * onChange.accept(key, newValue)

        where:
        key          | oldValue        | newValue
        'existing'   | 'existingValue' | 'changed'
        'missingKey' | null            | 'changed'
    }

    def "method put(#key, #newValue) updates map"() {
        given:
        def map = getMapUnderTestToWrite()

        when:
        map.put(key, newValue)

        then:
        map.get(key) == newValue

        where:
        key          | oldValue        | newValue
        'existing'   | 'existingValue' | 'changed'
        'missingKey' | null            | 'changed'
    }

    def "method putAll() reports argument as change only"() {
        when:
        def update = [existing: 'changedExisting', missing: 'changedMissing']
        getMapUnderTestToWrite().putAll(update)

        then:
        1 * onChange.accept('existing', 'changedExisting')
        1 * onChange.accept('missing', 'changedMissing')
        0 * onAccess._
    }

    def "method putIfAbsent(#key, newValue) reports change"() {
        when:
        def result = getMapUnderTestToWrite().putIfAbsent(key, 'newValue')

        then:
        result == oldValue
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, 'newValue')
        then:
        0 * onChange._
        0 * onAccess._

        where:
        key        | oldValue        | changeCount
        'existing' | 'existingValue' | 0
        'missing'  | null            | 1
    }

    def "method replaceAll changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        map.replaceAll((k, v) -> {
            if (k == 'existing') {
                return 'newValue'
            }
            return v
        })

        then:
        (Map<?, ?>) map == [existing: 'newValue', other: 'otherValue']
    }

    def "method replaceAll reports access to every item"() {
        when:
        getMapUnderTestToWrite().replaceAll((k, v) -> {
            if (k == 'existing') {
                return 'newValue'
            }
            return v
        })

        then:
        1 * onAccess.accept('existing', 'existingValue')
        1 * onAccess.accept('other', 'otherValue')
        then:
        0 * onAccess._
    }

    def "method replaceAll reports access before change"() {
        when:
        getMapUnderTestToWrite().replaceAll((k, v) -> {
            if (k == 'existing') {
                return 'newValue'
            }
            return v
        })

        then:
        1 * onAccess.accept('existing', 'existingValue')
        then:
        1 * onChange.accept('existing', 'newValue')
        then:
        0 * onChange._
    }

    def "method replace(#key, #oldValue, newValue) changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.replace(key, oldValue, 'newValue')

        then:
        result == expectedResult
        map[key] == expectedValue

        where:
        key        | oldValue        | expectedValue   | expectedResult
        'existing' | 'existingValue' | 'newValue'      | true
        'existing' | 'otherValue'    | 'existingValue' | false
        'missing'  | 'otherValue'    | null            | false

    }

    def "method replace(#key, #oldValue, newValue) reports change"() {
        when:
        getMapUnderTestToWrite().replace(key, oldValue, 'newValue')

        then:
        1 * onAccess.accept(key, expectedOldValue)
        then:
        changeCount * onChange.accept(key, 'newValue')
        then:
        0 * onChange._
        0 * onAccess._

        where:
        key        | expectedOldValue | oldValue         | changeCount
        'existing' | 'existingValue'  | expectedOldValue | 1
        'existing' | 'existingValue'  | 'otherValue'     | 0
        'missing'  | null             | 'otherValue'     | 0
    }

    def "method replace(#key, newValue) changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.replace(key, 'newValue')

        then:
        result == oldValue
        map[key] == expectedValue

        where:
        key        | oldValue        | expectedValue
        'existing' | 'existingValue' | 'newValue'
        'missing'  | null            | null
    }

    def "method replace(#key, newValue) reports change"() {
        when:
        getMapUnderTestToWrite().replace(key, 'newValue')

        then:
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, 'newValue')
        then:
        0 * onChange._
        0 * onAccess._

        where:
        key        | oldValue
        'existing' | 'existingValue'
        'missing'  | null

        changeCount = oldValue != null ? 1 : 0
    }

    def "method computeIfAbsent(#key, #newValue) changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.computeIfAbsent(key, k -> newValue)

        then:
        result == expectedResult
        map[key] == expectedResult

        where:
        key        | newValue   | expectedResult
        'existing' | 'newValue' | 'existingValue'
        'existing' | null       | 'existingValue'
        'missing'  | 'newValue' | 'newValue'
        'missing'  | null       | null
    }

    def "method computeIfAbsent(#key, #newValue) reports change"() {
        when:
        getMapUnderTestToWrite().computeIfAbsent(key, k -> newValue)

        then:
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, newValue)
        then:
        0 * onAccess._
        0 * onChange._
        0 * onRemove._

        where:
        key        | newValue   | oldValue        | changeCount
        'existing' | 'newValue' | 'existingValue' | 0
        'existing' | null       | 'existingValue' | 0
        'missing'  | 'newValue' | null            | 1
        'missing'  | null       | null            | 0
    }

    def "method computeIfPresent(#key, #newValue) changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.computeIfPresent(key, (k, v) -> newValue)

        then:
        result == expectedResult
        map[key] == expectedResult

        where:
        key        | newValue   | expectedResult
        'existing' | 'newValue' | 'newValue'
        'existing' | null       | null
        'missing'  | 'newValue' | null
        'missing'  | null       | null
    }

    def "method computeIfPresent(#key, #newValue) reports change"() {
        when:
        getMapUnderTestToWrite().computeIfPresent(key, (k, v) -> newValue)

        then:
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, newValue)
        removeCount * onRemove.accept(key)
        then:
        0 * onChange._
        0 * onAccess._
        0 * onRemove._

        where:
        key        | newValue   | oldValue        | changeCount | removeCount
        'existing' | 'newValue' | 'existingValue' | 1           | 0
        'existing' | null       | 'existingValue' | 0           | 1
        'missing'  | 'newValue' | null            | 0           | 0
        'missing'  | null       | null            | 0           | 0
    }

    def "method compute(#key, #newValue) changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.compute(key, (k, v) -> newValue)

        then:
        result == expectedResult
        map[key] == expectedResult

        where:
        key        | newValue   | expectedResult
        'existing' | 'newValue' | 'newValue'
        'existing' | null       | null
        'missing'  | 'newValue' | 'newValue'
        'missing'  | null       | null
    }

    def "method compute(#key, #newValue) reports change"() {
        when:
        getMapUnderTestToWrite().compute(key, (k, v) -> newValue)

        then:
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, newValue)
        removeCount * onRemove.accept(key)
        then:
        0 * onChange._
        0 * onAccess._
        0 * onRemove._

        where:
        key        | newValue   | oldValue        | changeCount | removeCount
        'existing' | 'newValue' | 'existingValue' | 1           | 0
        'existing' | null       | 'existingValue' | 0           | 1
        'missing'  | 'newValue' | null            | 1           | 0
        'missing'  | null       | null            | 0           | 0
    }

    def "method merge(#key, #newValue) changes map"(String key, String newValue, BiFunction<?, ?, ?> func, String expectedResult) {
        when:
        def map = getMapUnderTestToWrite()
        def result = map.merge(key, newValue, func)

        then:
        result == expectedResult
        map[key] == expectedResult

        where:
        key        | newValue   | func                                        | expectedResult
        'existing' | 'Concat'   | String::concat                              | 'existingValueConcat'
        'existing' | ''         | AccessTrackingPropertiesTest::removeMapping | null
        'missing'  | 'newValue' | String::concat                              | 'newValue'
    }

    def "method merge(#key, #newValue) reports change"(String key, String newValue, String oldValue, BiFunction<?, ?, ?> func) {
        when:
        getMapUnderTestToWrite().merge(key, newValue, func)

        then:
        1 * onAccess.accept(key, oldValue)
        then:
        changeCount * onChange.accept(key, changed)
        removeCount * onRemove.accept(key)

        then:
        0 * onAccess._
        0 * onChange._
        0 * onRemove._

        where:
        key        | newValue   | oldValue        | func                                        | changed              | removeCount
        'existing' | 'Concat'   | 'existingValue' | String::concat                              | "$oldValue$newValue" | 0
        'existing' | ''         | 'existingValue' | AccessTrackingPropertiesTest::removeMapping | null                 | 1
        'missing'  | 'newValue' | null            | String::concat                              | newValue             | 0

        changeCount = changed != null ? 1 : 0
    }

    def "entry method setValue changes map"() {
        when:
        def map = getMapUnderTestToWrite()
        def entry = map.entrySet().find { entry -> entry.getKey() == 'existing' }

        def result = entry.setValue('newValue')

        then:
        result == 'existingValue'
        map['existing'] == 'newValue'
    }

    def "entry method setValue reports change"() {
        when:
        def entry = getMapUnderTestToWrite().entrySet().find { entry -> entry.getKey() == 'existing' }

        // reset a mock
        entry.setValue('newValue')

        then:
        // 1st invocation comes from "find()" call, 2nd is for the setValue result.
        // TODO(mlopatkin) replace 1st call with a proper aggregating access handling.
        2 * onAccess.accept('existing', 'existingValue')
        then:
        1 * onChange.accept('existing', 'newValue')
        then:
        0 * onChange._
    }

    def "method clear() notifies listener and changes map"() {
        def map = getMapUnderTestToWrite()
        when:
        map.clear()

        then:
        map.isEmpty()
        1 * onClear.run()
        0 * onAccess._
        0 * onRemove._
        0 * onChange._
    }

    private static Properties propertiesWithContent(Map<String, String> contents) {
        Properties props = new Properties()
        props.putAll(contents)
        return props
    }

    // Shortcut to have a typed lambda expression in where: block
    private static Consumer<Properties> call(Consumer<Properties> consumer) {
        return consumer
    }

    private static Object removeMapping(Object key, Object value) {
        return null
    }
}
