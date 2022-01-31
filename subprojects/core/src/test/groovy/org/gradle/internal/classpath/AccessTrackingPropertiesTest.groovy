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

import java.util.function.Consumer

class AccessTrackingPropertiesTest extends AbstractAccessTrackingMapTest {
    @Override
    protected Properties getMapUnderTestToRead() {
        return getMapUnderTestToWrite()
    }

    protected Properties getMapUnderTestToWrite() {
        return new AccessTrackingProperties(propertiesWithContent(innerMap), consumer)
    }

    def "getProperty(#key) is tracked"() {
        when:
        def result = getMapUnderTestToRead().getProperty(key)

        then:
        result == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

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
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

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
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        when:
        def containsAllResult = getMapUnderTestToRead().stringPropertyNames().containsAll(Collections.singleton(key))

        then:
        containsAllResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

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
        1 * consumer.accept(key1, reportedValue1)
        1 * consumer.accept(key2, reportedValue2)
        0 * consumer._

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
        1 * consumer.accept(key, reportedValue)
        0 * consumer._
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
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        when:
        def removeAllResult = getMapUnderTestToWrite().keySet().removeAll(Collections.singleton(key))

        then:
        removeAllResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

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
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

        when:
        def removeAllResult = getMapUnderTestToWrite().entrySet().removeAll(Collections.singleton(entry(key, requestedValue)))

        then:
        removeAllResult == expectedResult
        1 * consumer.accept(key, reportedValue)
        0 * consumer._

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
        (1.._) * consumer.accept('existing', 'existingValue')
        (1.._) * consumer.accept('other', 'otherValue')
        0 * consumer._

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
        0 * consumer._
        where:
        methodName          | operation
        "toString()"        | call(p -> p.toString())
        "clear()"           | call(p -> p.clear())
        "load(Reader)"      | call(p -> p.load(new StringReader("")))
        "load(InputStream)" | call(p -> p.load(new ByteArrayInputStream(new byte[0])))
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
