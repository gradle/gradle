/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import org.gradle.messaging.dispatch.MethodInvocation
import spock.lang.Specification

class MethodInvocationSerializerTest extends Specification {
    final classLoader = new GroovyClassLoader(getClass().classLoader)
    final serializer = new MethodInvocationSerializer(classLoader)

    def "serializes a method invocation with parameters"() {
        def method = String.class.getMethod("substring", Integer.TYPE, Integer.TYPE)
        def invocation = new MethodInvocation(method, [1, 2] as Object[])

        when:
        def serialized = serialize(invocation)
        def result = deserialize(serialized)

        then:
        result.method == method
        result.arguments == [1, 2] as Object[]
        serialized.length == 37
    }

    def "replaces a method that has already been seen with an integer ID"() {
        def method1 = String.class.getMethod("substring", Integer.TYPE, Integer.TYPE)
        def method2 = String.class.getMethod("substring", Integer.TYPE, Integer.TYPE)
        def invocation1 = new MethodInvocation(method1, [1, 2] as Object[])
        def invocation2 = new MethodInvocation(method2, [3, 4] as Object[])

        when:
        def serialized = serialize(invocation1, invocation2)
        def result = deserializeMultiple(serialized, 2)

        then:
        result[0].method == method1
        result[0].arguments == [1, 2] as Object[]
        result[1].method == method1
        result[1].arguments == [3, 4] as Object[]
        serialized.length == 42
    }

    def "serializes method invocations for multiple methods"() {
        def method1 = String.class.getMethod("substring", Integer.TYPE, Integer.TYPE)
        def method2 = String.class.getMethod("substring", Integer.TYPE)
        def invocation1 = new MethodInvocation(method1, [1, 2] as Object[])
        def invocation2 = new MethodInvocation(method2, [3] as Object[])
        def invocation3 = new MethodInvocation(method1, [4, 5] as Object[])

        when:
        def serialized = serialize(invocation1, invocation2, invocation3)
        def result = deserializeMultiple(serialized, 3)

        then:
        result[0].method == method1
        result[0].arguments == [1, 2] as Object[]
        result[1].method == method2
        result[1].arguments == [3] as Object[]
        result[2].method == method1
        result[2].arguments == [4, 5] as Object[]
    }

    def "uses provided ClassLoader to locate incoming method invocation"() {
        Class cl = classLoader.parseClass('package org.gradle.test; class TestObj { void doStuff() { } }')
        def method = cl.getMethod("doStuff")
        def invocation = new MethodInvocation(method, [] as Object[])

        when:
        def serialized = serialize(invocation)
        def result = deserialize(serialized)

        then:
        result.method == method
        result.arguments == [] as Object[]
    }

    def serialize(MethodInvocation... messages) {
        def outStr = new ByteArrayOutputStream()
        def output = new Output(outStr)
        def writer = serializer.newWriter(output)
        messages.each {
            writer.write(it)
        }
        output.flush()
        return outStr.toByteArray()
    }

    def deserialize(byte[] data) {
        return serializer.newReader(new Input(data)).read()
    }

    def deserializeMultiple(byte[] data, int count) {
        def reader = serializer.newReader(new Input(data))
        def result = []
        count.times {
            result << reader.read()
        }
        return result
    }
}
