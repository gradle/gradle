/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.Transformer
import org.gradle.internal.Describables
import org.gradle.internal.state.ManagedFactory

import javax.annotation.Nullable

class AbstractMinimalProviderTest extends ProviderSpec<String> {
    TestProvider<String> provider = new TestProvider(String)

    @Override
    TestProvider<String> providerWithNoValue() {
        return new TestProvider(String)
    }

    @Override
    TestProvider<String> providerWithValue(String value) {
        def p = new TestProvider(String)
        p.value = value
        return p
    }

    @Override
    Class<String> type() {
        return String
    }

    @Override
    String someValue() {
        "s2"
    }

    @Override
    String someOtherValue() {
        "other1"
    }

    @Override
    String someOtherValue2() {
        "other2"
    }

    @Override
    String someOtherValue3() {
        "other3"
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.ProviderManagedFactory()
    }

    def "is present when value is not null"() {
        expect:
        !provider.present
        provider.value("s1")
        provider.present
    }

    def "can query with default when value is null"() {
        expect:
        provider.getOrNull() == null
        provider.getOrElse("s2") == "s2"
    }

    def "mapped provider is live"() {
        def transformer = Stub(Transformer)
        transformer.transform(_) >> { String s -> "[$s]" }

        expect:
        def mapped = provider.map(transformer)
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse("s2") == "s2"

        provider.value("abc")
        mapped.present
        mapped.get() == "[abc]"

        provider.value(null)
        !mapped.present

        provider.value("123")
        mapped.present
        mapped.get() == "[123]"
    }

    def "can chain mapped providers"() {
        def transformer1 = Stub(Transformer)
        transformer1.transform(_) >> { String s -> "[$s]" as String }
        def transformer2 = Stub(Transformer)
        transformer2.transform(_) >> { String s -> "-$s-" as String }

        expect:
        def mapped = provider.map(transformer1).map(transformer2)
        !mapped.present
        mapped.getOrNull() == null
        mapped.getOrElse("s2") == "s2"

        provider.value("abc")
        mapped.present
        mapped.get() == "-[abc]-"
    }

    def "cannot query mapped value when value is null"() {
        def transformer = Stub(Transformer)
        def provider = provider.map(transformer)

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot query the value of this provider because it has no value available.'
    }

    def "toString() displays nice things"() {
        expect:
        new TestProvider(String).toString() == "provider(java.lang.String)"
    }

    def "exception contains classloader info when the same class loaded with different classloaders"() {
        given:
        def clSrc = new TestClassLoader("src")
        def clTarget = new TestClassLoader("target")

        def sourceType = clSrc.loadClass(TestValue.name)
        def targetType = clTarget.loadClass(TestValue.name)

        def provider = new TestProvider(sourceType)

        when:
        provider.asSupplier(Describables.of("someProp"), targetType, ValueSanitizers.forType(targetType))

        then:
        def e = thrown(IllegalArgumentException)

        e.message == "Cannot set the value of someProp of type ${TestValue.name} loaded with TestClassLoader(target) using a provider of type ${TestValue.name} loaded with TestClassLoader(src)."
    }

    def "exception contains no classloader info when different types are requested"() {
        given:
        def targetType = String
        def sourceType = Integer
        def provider = new TestProvider(sourceType)

        when:
        provider.asSupplier(Describables.of("someProp"), targetType, ValueSanitizers.forType(targetType))

        then:
        def e = thrown(IllegalArgumentException)

        e.message == "Cannot set the value of someProp of type ${targetType.name} using a provider of type ${sourceType.name}."
    }

    static class TestProvider<T> extends AbstractMinimalProvider<T> {
        final Class<T> cls

        @Nullable
        T value

        TestProvider(Class<T> cls) {
            this.cls = cls
        }

        void value(T value) {
            this.value = value
        }

        @Override
        Class<T> getType() {
            return cls
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return Value.ofNullable(value)
        }
    }

    static class TestClassLoader extends URLClassLoader {
        private final String name

        TestClassLoader(String name) {
            super(new URL[]{TestValue.protectionDomain.codeSource.location}, null as ClassLoader)
            this.name = name
        }

        @Override
        String toString() { "TestClassLoader(${name})" }
    }
}
