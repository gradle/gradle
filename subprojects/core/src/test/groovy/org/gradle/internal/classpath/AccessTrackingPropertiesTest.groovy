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

import com.google.common.io.ByteStreams
import com.google.common.io.CharStreams

import javax.annotation.Nullable
import java.util.function.BiConsumer
import java.util.function.Consumer

class AccessTrackingPropertiesTest extends AbstractAccessTrackingMapTest {
    private BiConsumer<Object, Object> onChange = Mock()

    private AccessTrackingProperties.Listener listener = new AccessTrackingProperties.Listener() {
        @Override
        void onAccess(Object key, @Nullable Object value) {
            onAccess.accept(key, value)
        }

        @Override
        void onChange(Object key, @Nullable Object newValue) {
            onChange.accept(key, newValue)
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
        0 * onAccess._
        where:
        key        | reportedValue   | expectedResult
        'existing' | 'existingValue' | 'existingValue'
        'missing'  | null            | null
    }

    def "keySet() remove(#key) and removeAll(#key) are tracked"() {
        when:
        def removeResult = getMapUnderTestToWrite().keySet().remove(key)

        then:
        removeResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

        when:
        def removeAllResult = getMapUnderTestToWrite().keySet().removeAll(Collections.singleton(key))

        then:
        removeAllResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

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
        0 * onAccess._

        when:
        def removeAllResult = getMapUnderTestToWrite().entrySet().removeAll(Collections.singleton(entry(key, requestedValue)))

        then:
        removeAllResult == expectedResult
        1 * onAccess.accept(key, reportedValue)
        0 * onAccess._

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
        operation.accept(getMapUnderTestToRead())

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

    private static Properties propertiesWithContent(Map<String, String> contents) {
        Properties props = new Properties()
        props.putAll(contents)
        return props
    }

    // Shortcut to have a typed lambda expression in where: block
    private static Consumer<Properties> call(Consumer<Properties> consumer) {
        return consumer
    }
}
