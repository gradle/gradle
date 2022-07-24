/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.tooling.internal.provider.AbstractClassGraphSpec
import org.gradle.tooling.internal.provider.CustomPayload
import org.gradle.tooling.internal.provider.PayloadInterface
import org.gradle.tooling.internal.provider.WrapperPayload
import org.junit.Assert
import spock.lang.Ignore

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class PayloadSerializerTest extends AbstractClassGraphSpec {
    final PayloadSerializer originator = new PayloadSerializer(new WellKnownClassLoaderRegistry(new DefaultPayloadClassLoaderRegistry(new ClassLoaderCache(), new ModelClassLoaderFactory())))
    final PayloadSerializer receiver = new PayloadSerializer(new WellKnownClassLoaderRegistry(new DefaultPayloadClassLoaderRegistry(new ClassLoaderCache(), new ModelClassLoaderFactory())))

    def "can send an object between two parties"() {
        expect:
        def serialized = originator.serialize(source)
        receiver.deserialize(serialized) == source

        where:
        source       | _
        null         | _
        "some value" | _
    }

    def "implementation classpath travels with object"() {
        def payloadClass = isolated(CustomPayload, PayloadInterface).loadClass(CustomPayload.name)
        def original = payloadClass.newInstance(value: 'value')

        when:
        def serialized = originator.serialize(original)
        def received = receiver.deserialize(serialized)

        then:
        received.class != payloadClass
        received.class != CustomPayload
        received.class.name == CustomPayload.class.name
        received.value == 'value'
    }

    def "uses system ClassLoader when original object is loaded by system ClassLoader"() {
        when:
        def serialized = originator.serialize(new StringBuilder('value'))
        def received = receiver.deserialize(serialized)

        then:
        received.class == StringBuilder.class
        received.toString() == 'value'
    }

    def "uses original ClassLoader when receiving a reply"() {
        def payloadClass = isolated(CustomPayload, PayloadInterface).loadClass(CustomPayload.name)
        def original = payloadClass.newInstance(value: 'value')

        when:
        def serialized = originator.serialize(original)
        def received = receiver.deserialize(serialized)
        def reply = originator.deserialize(receiver.serialize(received))

        then:
        received.class != CustomPayload.class
        received.class != payloadClass
        reply.class == payloadClass
    }

    def "handles nested objects which are not visible from root object ClassLoader"() {
        def parent = isolated(WrapperPayload, PayloadInterface)
        def wrapperClass = parent.loadClass(WrapperPayload.name)
        def payloadClass = isolated(parent, CustomPayload).loadClass(CustomPayload.name)
        assertNotVisible(wrapperClass, payloadClass)
        def original = wrapperClass.newInstance(payload: payloadClass.newInstance(value: 'value'))

        when:
        def serialized = originator.serialize(original)
        def received = receiver.deserialize(serialized)

        then:
        received.class != wrapperClass
        received.class.name == WrapperPayload.name
        received.payload.class != payloadClass
        received.payload.class.name == CustomPayload.name
        received.class.classLoader != received.payload.class.classLoader
    }

    @Ignore("work in progress")
    def "handles objects in separate ClassLoaders with shared parent"() {
        def filter = filter(PayloadInterface)
        def wrapperClass = isolated(filter, WrapperPayload).loadClass(WrapperPayload.name)
        def payloadClass = isolated(filter, CustomPayload).loadClass(CustomPayload.name)
        assertNotVisible(wrapperClass, payloadClass)
        assertNotVisible(payloadClass, wrapperClass)
        def original = wrapperClass.newInstance(payload: payloadClass.newInstance(value: 'value'))

        when:
        def received = receiver.deserialize(originator.serialize(original))
        def reply = originator.deserialize(receiver.serialize(received))

        then:
        received.class.classLoader.loadClass(PayloadInterface.name) == received.payload.class.classLoader.loadClass(PayloadInterface.name)
        reply instanceof PayloadInterface
        reply.value instanceof PayloadInterface
        reply.class == wrapperClass
        reply.payload.class == payloadClass
    }

    def "can send a dynamic proxy"() {
        def cl = isolated(PayloadInterface)
        def payloadClass = cl.loadClass(PayloadInterface.name)
        def original = Proxy.newProxyInstance(cl, [payloadClass] as Class[], new GroovyInvocationHandler())

        when:
        def received = receiver.deserialize(originator.serialize(original))
        def reply = originator.deserialize(receiver.serialize(received))

        then:
        received.class != original.class
        !payloadClass.isInstance(received)
        Proxy.getInvocationHandler(received).class != GroovyInvocationHandler.class
        received.value == "result!"

        payloadClass.isInstance(reply)
        Proxy.getInvocationHandler(reply).class == GroovyInvocationHandler.class
        reply.value == "result!"
    }

    def "can send a Class instance"() {
        def cl = isolated(PayloadInterface).loadClass(PayloadInterface.name)

        when:
        def serialized = originator.serialize(cl)
        def received = receiver.deserialize(serialized)

        then:
        received != cl
        received.name == cl.name
    }

    def "reuses ClassLoaders for multiple invocations"() {
        def cl = isolated(WrapperPayload, CustomPayload, PayloadInterface)
        def wrapperClass = cl.loadClass(WrapperPayload.name)
        def payloadClass = cl.loadClass(CustomPayload.name)
        def original = wrapperClass.newInstance(payload: payloadClass.newInstance(value: 'value'))

        when:
        def received1 = receiver.deserialize(originator.serialize(original))
        def reply1 = originator.deserialize(receiver.serialize(received1))
        def received2 = receiver.deserialize(originator.serialize(original))
        def reply2 = originator.deserialize(receiver.serialize(received2))

        then:
        received1.class.classLoader == received2.class.classLoader
        received1.payload.class.classLoader == received2.payload.class.classLoader
        reply1.class == wrapperClass
        reply1.payload.class == payloadClass
        reply2.class == wrapperClass
        reply2.payload.class == payloadClass
    }

    void assertNotVisible(Class<?> from, Class<?> to) {
        try {
            from.classLoader.loadClass(to.name)
            Assert.fail()
        } catch (ClassNotFoundException) {
            // expected
        }
    }

    ClassLoader filter(Class<?> aClass) {
        def spec = new FilteringClassLoader.Spec()
        spec.allowClass(aClass)
        return new FilteringClassLoader(aClass.classLoader, spec)
    }

    ClassLoader isolated(ClassLoader parent = ClassLoader.systemClassLoader.parent, Class<?>... classes) {
        def classpath = isolatedClasses(classes)
        def loader = urlClassLoader(parent, classpath)
        for (Class<?> aClass : classes) {
            assert loader.loadClass(aClass.name) != aClass
        }
        return loader
    }

    private static class GroovyInvocationHandler implements InvocationHandler, Serializable {
        Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return "result!"
        }
    }
}
