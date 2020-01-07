/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.internal.Describables
import org.gradle.internal.state.Managed

import java.util.concurrent.Callable

abstract class PropertySpec<T> extends ProviderSpec<T> {
    @Override
    abstract PropertyInternal<T> providerWithValue(T value)

    @Override
    PropertyInternal<T> providerWithNoValue() {
        return propertyWithNoValue()
    }

    /**
     * Returns a property with _no_ value.
     */
    abstract PropertyInternal<T> propertyWithNoValue()

    /**
     * Returns a property with its default value.
     */
    abstract PropertyInternal<T> propertyWithDefaultValue()

    abstract T someValue()

    abstract T someOtherValue()

    abstract Class<T> type()

    @Override
    String getDisplayName() {
        return "this property"
    }

    protected void setToNull(def property) {
        property.set(null)
    }

    def "cannot get value when it has none"() {
        given:
        def property = propertyWithNoValue()

        when:
        property.get()

        then:
        def t = thrown(IllegalStateException)
        t.message == "No value has been specified for ${displayName}."

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.get()

        then:
        def t2 = thrown(IllegalStateException)
        t2.message == "No value has been specified for <display-name>."

        when:
        property.set(someValue())
        property.get()

        then:
        noExceptionThrown()
    }

    def "can set value"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "can set value using chaining method"() {
        given:
        def property = propertyWithNoValue()
        property.value(someValue())

        expect:
        property.get() == someValue()
    }

    def "can set value using provider"() {
        def provider = provider(someValue(), someValue(), someOtherValue(), someValue())

        given:
        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someOtherValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrNull() == null
    }

    def "can set value using provider and chaining method"() {
        given:
        def property = propertyWithNoValue()
        property.value(Providers.of(someValue()))

        expect:
        property.get() == someValue()
    }

    def "does not allow a null provider"() {
        given:
        def property = propertyWithNoValue()

        when:
        property.set((Provider) null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot set the value of a property using a null provider.'
    }

    def "can set untyped using null"() {
        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someOtherValue()) == someOtherValue()
    }

    def "can set untyped using value"() {
        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(someValue())

        expect:
        property.present
        property.get() == someValue()
        property.getOrNull() == someValue()
        property.getOrElse(someOtherValue()) == someValue()
        property.getOrElse(null) == someValue()
    }

    def "fails when untyped value is set using incompatible type"() {
        def property = propertyWithNoValue()

        when:
        property.setFromAnyValue(new Thing())

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot set the value of a property of type ${type().name} using an instance of type ${Thing.name}."
    }

    def "can set untyped using provider"() {
        def provider = provider(someValue(), someValue())

        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(provider)

        expect:
        property.present
        property.get() == someValue()
    }

    def "convention value is used before value has been set"() {
        def property = propertyWithDefaultValue()
        assert property.getOrNull() != someValue()

        expect:
        property.convention(someValue())
        property.present
        property.get() == someValue()

        property.set(someOtherValue())
        property.present
        property.get() == someOtherValue()
    }

    def "convention provider is used before value has been set"() {
        def provider = provider(someValue(), someOtherValue(), someValue())
        def property = propertyWithDefaultValue()

        when:
        property.convention(provider)

        then:
        property.present
        property.get() == someOtherValue()
        property.get() == someValue()

        when:
        property.set(someOtherValue())

        then:
        property.present
        property.get() == someOtherValue()
    }

    def "can replace convention value before value has been set"() {
        def provider = provider(someOtherValue())
        def property = propertyWithDefaultValue()

        when:
        property.convention(someValue())

        then:
        property.get() == someValue()

        when:
        property.convention(provider)

        then:
        property.get() == someOtherValue()

        when:
        property.convention(someValue())

        then:
        property.get() == someValue()

        when:
        property.set(someOtherValue())

        then:
        property.get() == someOtherValue()
    }

    def "convention value ignored after value has been set"() {
        def property = propertyWithDefaultValue()
        property.set(someValue())

        expect:
        property.convention(someOtherValue())
        property.get() == someValue()
    }

    def "convention provider ignored after value has been set"() {
        def provider = broken()

        def property = propertyWithDefaultValue()
        property.set(someValue())

        expect:
        property.convention(provider)
        property.get() == someValue()
    }

    def "convention value is used after value has been set to null"() {
        def property = propertyWithDefaultValue()

        property.convention(someOtherValue())
        setToNull(property)

        expect:
        property.present
        property.get() == someOtherValue()

        property.convention(someValue())
        property.present
        property.get() == someValue()
    }

    def "convention provider is used after value has been set to null"() {
        def provider = provider(someOtherValue(), someOtherValue())

        def property = propertyWithDefaultValue()
        property.convention(provider)
        property.set(someValue())
        setToNull(property)

        expect:
        property.present
        property.get() == someOtherValue()
    }

    def "convention value ignored after value has been set using provider with no value"() {
        def property = propertyWithDefaultValue()
        property.set(Providers.notDefined())

        expect:
        property.convention(someOtherValue())
        !property.present
        property.getOrNull() == null
    }

    def "convention provider ignored after value has been set using provider with no value"() {
        def provider = broken()

        def property = propertyWithDefaultValue()
        property.set(Providers.notDefined())

        expect:
        property.convention(provider)
        !property.present
        property.getOrNull() == null
    }

    def "can map value using a transformation"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == someOtherValue()
        1 * transformer.transform(someValue()) >> someOtherValue()
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == someValue()
        1 * transformer.transform(someValue()) >> someValue()
        0 * _
    }

    def "transformation is provided with the current value of the property each time the value is queried"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 123
        1 * transformer.transform(someValue()) >> 123
        0 * _

        when:
        property.set(someOtherValue())
        def r2 = provider.get()

        then:
        r2 == 456
        1 * transformer.transform(someOtherValue()) >> 456
        0 * _
    }

    def "can map value to some other type"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == 12
        1 * transformer.transform(someValue()) >> 12
        0 * _

        when:
        def r2 = provider.get()

        then:
        r2 == 10
        1 * transformer.transform(someValue()) >> 10
        0 * _
    }

    def "mapped provider has no value and transformer is not invoked when property has no value"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()

        when:
        def provider = property.map(transformer)

        then:
        !provider.present
        provider.getOrNull() == null
        provider.getOrElse(someOtherValue()) == someOtherValue()
        0 * _

        when:
        provider.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "No value has been specified for ${displayName}."
    }

    def "can finalize value when no value defined"() {
        def property = propertyWithNoValue()

        when:
        property."$method"()

        then:
        !property.present
        property.getOrNull() == null

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "can finalize value when value set"() {
        def property = propertyWithNoValue()

        when:
        property.set(someValue())
        property."$method"()

        then:
        property.present
        property.getOrNull() == someValue()

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "can finalize value when using convention"() {
        def property = propertyWithDefaultValue()

        when:
        property.convention(someValue())
        property."$method"()

        then:
        property.present
        property.getOrNull() == someValue()

        where:
        method << ["finalizeValue", "implicitFinalizeValue"]
    }

    def "replaces provider with fixed value when value finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        present
        result == someValue()
        0 * _
    }

    def "replaces provider with fixed value on next query of value when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def result = property.get()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "replaces provider with fixed value on next query of nullable value when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def result = property.getOrNull()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "replaces provider with fixed value on next query of value with default when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def result = property.getOrElse(someOtherValue())
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "replaces provider with fixed value on next query of `present` property when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def present = property.present
        def value = property.get()

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        present
        value == someValue()
    }

    def "replaces provider with fixed value on next query of mapped value when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def value = property.map { it }.get()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        present
        value == someValue()
    }

    def "replaces provider with fixed value on next query of orElse value when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def value = property.orElse(someOtherValue()).get()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        present
        value == someValue()
    }

    def "replaces provider with fixed value on next query of value when value finalized on read"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValueOnRead()

        then:
        0 * _

        when:
        def result = property.get()
        def present = property.present

        then:
        1 * function.call() >> someValue()
        0 * _

        and:
        result == someValue()
        present
    }

    def "does not finalize value when property is used as RHS of orElse provider when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def value = Providers.of(someValue()).orElse(property).get()

        then:
        0 * _

        and:
        value == someValue()
    }

    def "replaces provider with fixed value when value finalized after value implicitly finalized but not read yet"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)
        property.implicitFinalizeValue()

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        0 * _

        and:
        present
        result == someValue()
    }

    def "replaces provider with no value with fixed missing value when value finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> null
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        !present
        result == null
        0 * _
    }

    def "replaces provider with no value with fixed missing value on next query when value implicitly finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.implicitFinalizeValue()

        then:
        0 * _

        when:
        def present = property.present
        def result = property.getOrNull()

        then:
        1 * function.call() >> null
        0 * _

        and:
        !present
        result == null
    }

    def "can finalize value when already finalized"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        when:
        property.set(provider)
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _

        when:
        property.finalizeValue()
        property.implicitFinalizeValue()
        property.implicitFinalizeValue()
        property.disallowChanges()

        then:
        0 * _
    }

    def "can finalize after changes disallowed"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        when:
        property.set(provider)
        property.disallowChanges()

        then:
        0 * _

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> someValue()
        0 * _
    }

    def "continues to use value from provider after changes disallowed"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)

        given:
        property.set(provider)

        when:
        property.disallowChanges()

        then:
        0 * function._

        when:
        def result = property.getOrNull()

        then:
        result == someValue()
        1 * function.call() >> someValue()
        0 * _
    }

    def "cannot set value after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value after value finalized implicitly and before queried"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set value after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.get()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value after value finalized after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()
        property.finalizeValue()

        when:
        property.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someOtherValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "can set value after value finalized on read and before queried"() {
        given:
        def property = propertyWithNoValue()
        property.set(someOtherValue())
        property.finalizeValueOnRead()

        when:
        property.set(someValue())
        def result = property.get()

        then:
        result == someValue()
    }

    def "cannot set value after value queried and value finalized on read"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(someOtherValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        setToNull(property)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(someValue())

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set value after changes disallowed and implicitly finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()
        property.implicitFinalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set value after changes disallowed and finalized on read and value not queried"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.disallowChanges()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set value after changes disallowed and finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()
        property.finalizeValue()

        when:
        property.set(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set value using provider after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(Mock(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value using provider after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(Mock(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set value using provider after value queried and value finalized on read"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(Mock(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value using provider after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.set(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.value(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.set(Mock(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set value using any type after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.setFromAnyValue(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value using any type after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.setFromAnyValue(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set value using any type after value queried and value finalized on read"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.setFromAnyValue(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set value using any type after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.setFromAnyValue(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.setFromAnyValue(Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.setFromAnyValue(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value after value finalized"() {
        given:
        def property = propertyWithDefaultValue()
        property.finalizeValue()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value after value finalized implicitly"() {
        given:
        def property = propertyWithDefaultValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value after value queried and value finalized on read"() {
        given:
        def property = propertyWithDefaultValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value after changes disallowed"() {
        given:
        def property = propertyWithDefaultValue()
        property.disallowChanges()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value using provider after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value using provider after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value using provider after value queried and value finalized on read"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value using provider after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.convention(Mock(ProviderInternal))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachDisplayName(Describables.of("<display-name>"))
        property.convention(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "producer task for a property is not known by default"() {
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(someValue())

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        !known
        0 * context._
    }

    def "can define producer task for a property"() {
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(someValue())
        property.attachProducer(task)

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        known
        1 * context.add(task)
        0 * context._
    }

    def "has build dependencies when value is provider with producer task"() {
        def producer = "some task"
        def provider = withProducer(producer)
        def context = Mock(TaskDependencyResolveContext)
        def property = propertyWithNoValue()
        property.set(provider)

        when:
        def known = property.maybeVisitBuildDependencies(context)

        then:
        known
        1 * context.add(producer)
        0 * context._
    }

    def "has content producer when producer task attached"() {
        def task = Mock(Task)
        def property = propertyWithDefaultValue()

        expect:
        assertContentIsNotProducedByTask(property)
        !property.valueProducedByTask

        property.attachProducer(task)

        assertContentIsProducedByTask(property, task)
        !property.valueProducedByTask
    }

    def "has content producer when value is provider with content producer"() {
        def task = Mock(Task)
        def provider = contentProducedByTask(task)

        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        assertContentIsProducedByTask(property, task)
        !property.valueProducedByTask
    }

    def "mapped value has value producer when producer task attached to original property"() {
        def task = Mock(Task)
        def property = propertyWithDefaultValue()
        def mapped = property.map { it }

        expect:
        assertContentIsNotProducedByTask(mapped)
        !mapped.valueProducedByTask

        property.attachProducer(task)

        assertContentIsProducedByTask(mapped, task)
        mapped.valueProducedByTask
    }

    def "chain of mapped value has value producer when producer task attached to original property"() {
        def task = Mock(Task)
        def property = propertyWithDefaultValue()
        def mapped = property.map { it }.map { it }.map { it }

        expect:
        assertContentIsNotProducedByTask(mapped)
        !mapped.valueProducedByTask

        property.attachProducer(task)

        assertContentIsProducedByTask(mapped, task)
        mapped.valueProducedByTask
    }

    def "mapped value has value producer when value is provider with content producer"() {
        def task = Mock(Task)
        def provider = contentProducedByTask(task)

        def property = propertyWithNoValue()
        property.set(provider)
        def mapped = property.map { it }

        expect:
        assertContentIsProducedByTask(mapped, task)
        mapped.valueProducedByTask
    }

    def "can unpack state and recreate instance"() {
        given:
        def property = propertyWithNoValue()

        expect:
        property instanceof Managed
        !property.isImmutable()
        def state = property.unpackState()
        def copy = managedFactory().fromState(property.publicType(), state)
        !copy.is(property)
        !copy.present
        copy.getOrNull() == null

        property.set(someValue())
        copy.getOrNull() == null

        def state2 = property.unpackState()
        def copy2 = managedFactory().fromState(property.publicType(), state2)
        !copy2.is(property)
        copy2.get() == someValue()

        property.set(someOtherValue())
        copy.getOrNull() == null
        copy2.get() == someValue()
    }

    void assertContentIsNotProducedByTask(ProviderInternal<?> provider) {
        def producers = []
        provider.visitProducerTasks { producers.add(it) }
        assert producers.isEmpty()
    }

    void assertContentIsProducedByTask(ProviderInternal<?> provider, Task task) {
        def producers = []
        provider.visitProducerTasks { producers.add(it) }
        assert producers == [task]
    }

    ProviderInternal<T> broken() {
        return new AbstractReadOnlyProvider<T>() {
            @Override
            Class<T> getType() {
                return PropertySpec.this.type()
            }

            @Override
            T getOrNull() {
                throw new RuntimeException("broken!")
            }
        }
    }

    /**
     * A provider that provides one of given values each time it is queried, in the order given.
     */
    ProviderInternal<T> provider(T... values) {
        return new TestProvider<T>(type(), values as List<T>, null)
    }

    ProviderInternal<T> withProducer(Object value) {
        return new TestProvider<T>(type(), [], value)
    }

    ProviderInternal<T> contentProducedByTask(Task producer) {
        return new TestProvider<T>(type(), [], producer)
    }

    class TestProvider<T> extends AbstractReadOnlyProvider<T> {
        final Class<T> type
        final Iterator<T> values
        final Object producer

        TestProvider(Class<T> type, List<T> values, Object producer) {
            this.producer = producer
            this.values = values.iterator()
            this.type = type
        }

        @Override
        void visitProducerTasks(Action<? super Task> visitor) {
            if (producer != null) {
                visitor.execute(producer)
            }
        }

        @Override
        boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (producer != null) {
                context.add(producer)
                return true
            } else {
                return false
            }
        }

        @Override
        T getOrNull() {
            return values.hasNext() ? values.next() : null
        }
    }

    static class Thing {}
}
