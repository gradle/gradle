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
import org.gradle.api.provider.Provider
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.state.Managed
import org.gradle.internal.state.ModelObject
import org.gradle.util.internal.TextUtil

import java.util.concurrent.Callable
import java.util.function.Predicate

abstract class PropertySpec<T> extends ProviderSpec<T> {
    @Override
    PropertyInternal<T> providerWithValue(T value) {
        return propertyWithNoValue().value(value)
    }

    @Override
    PropertyInternal<T> providerWithNoValue() {
        return propertyWithNoValue()
    }

    /**
     * Returns a property with _no_ value.
     */
    abstract PropertyInternal<T> propertyWithNoValue()

    @Override
    String getDisplayName() {
        return "this property"
    }

    protected void setToNull(def property) {
        property.set(null)
    }

    protected void nullConvention(def property) {
        property.convention(null)
    }

    def host = Mock(PropertyHost)

    def "cannot get value when it has none"() {
        given:
        def property = propertyWithNoValue()

        when:
        property.get()

        then:
        def t = thrown(MissingValueException)
        t.message == "Cannot query the value of ${displayName} because it has no value available."

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.get()

        then:
        def t2 = thrown(MissingValueException)
        t2.message == "Cannot query the value of <display-name> because it has no value available."

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
        def provider = supplierWithValues(someValue(), someOtherValue(), someValue())

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

    def "can set value using provider with chaining method"() {
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

    def "reports failure to query value from provider and property has display name"() {
        given:
        def property = propertyWithNoValue()
        property.set(brokenSupplier())
        property.attachOwner(owner(), displayName("<property>"))

        when:
        property.present

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        e.message == "Failed to query the value of <property>."
        e.cause.message == "broken!"

        when:
        property.get()

        then:
        def e2 = thrown(AbstractProperty.PropertyQueryException)
        e2.message == "Failed to query the value of <property>."
        e2.cause.message == "broken!"
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
        def provider = supplierWithValues(someValue(), someValue())

        given:
        def property = propertyWithNoValue()
        property.setFromAnyValue(provider)

        expect:
        property.present
        property.get() == someValue()
    }

    def "convention value is used before value has been set"() {
        def property = propertyWithNoValue()
        assert property.getOrNull() != someValue()

        expect:
        property.convention(someValue())
        property.present
        property.get() == someValue()

        property.set(someOtherValue())
        property.present
        property.get() == someOtherValue()
    }

    def "can use null convention value"() {
        def property = propertyWithNoValue()
        assert property.getOrNull() != someValue()

        expect:
        nullConvention(property)

        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
    }

    def "convention provider is used before value has been set"() {
        def provider = supplierWithValues(someOtherValue(), someValue(), someValue())
        def property = propertyWithNoValue()

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

    def "property has no value when convention provider has no value"() {
        def provider = supplierWithNoValue()
        def property = propertyWithNoValue()

        when:
        property.convention(provider)

        then:
        !property.present

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "reports the source of convention provider when value is missing and source is known"() {
        def provider = supplierWithNoValue(Describables.of("<source>"))
        def property = propertyWithNoValue()

        given:
        property.convention(provider)

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of ${displayName} because it has no value available.
The value of this property is derived from: <source>""")
    }

    def "can replace convention value before value has been set"() {
        def provider = supplierWithValues(someOtherValue())
        def property = propertyWithNoValue()

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
        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        property.convention(someOtherValue())
        property.get() == someValue()
    }

    def "convention provider ignored after value has been set"() {
        def provider = brokenSupplier()

        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        property.convention(provider)
        property.get() == someValue()
    }

    def "convention value is used after value has been set to null"() {
        def property = propertyWithNoValue()

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
        def provider = supplierWithValues(someOtherValue(), someOtherValue())

        def property = propertyWithNoValue()
        property.convention(provider)
        property.set(someValue())
        setToNull(property)

        expect:
        property.present
        property.get() == someOtherValue()
    }

    def "convention value ignored after value has been set using provider with no value"() {
        def property = propertyWithNoValue()
        property.set(Providers.notDefined())

        expect:
        property.convention(someOtherValue())
        !property.present
        property.getOrNull() == null
    }

    def "convention provider ignored after value has been set using provider with no value"() {
        def provider = brokenSupplier()

        def property = propertyWithNoValue()
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

    def "can filter value"() {
        def predicate = Mock(Predicate)
        def property = propertyWithNoValue()

        when:
        def provider = property.filter(predicate)

        then:
        0 * _

        when:
        property.set(someValue())
        def r1 = provider.get()

        then:
        r1 == someValue()
        1 * predicate.test(someValue()) >> true
        0 * _

        when:
        def r2 = provider.getOrNull()

        then:
        r2 == null
        1 * predicate.test(someValue()) >> false
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
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this provider because it has no value available."
    }

    def "reports the source of mapped provider when value is missing and source is known"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()
        property.attachOwner(owner(), displayName("<a>"))

        def provider = property.map(transformer)

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from: <a>""")
    }

    def "reports the source of flat mapped provider when value is missing and source is known"() {
        def transformer = Mock(Transformer)
        def property = propertyWithNoValue()
        property.attachOwner(owner(), displayName("<a>"))

        def provider = property.flatMap(transformer)

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from: <a>""")
    }

    def "reports the source of flat mapped provider when mapped value is missing and its source is known"() {
        def property = propertyWithNoValue()
        property.set(someValue())

        def other = propertyWithNoValue()
        other.attachOwner(owner(), displayName("<a>"))

        def provider = property.flatMap { other }

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from: <a>""")
    }

    def "reports the source of orElse provider when both values are missing and its source is known"() {
        def property = propertyWithNoValue()
        property.attachOwner(owner(), displayName("<a>"))

        def other = propertyWithNoValue()
        other.attachOwner(owner(), displayName("<b>"))

        def provider = property.orElse(other)

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from:
  - <a>
  - <b>""")
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
        def property = propertyWithNoValue()

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

    def "reports failure to query provider value when finalizing and property has display name"() {
        def property = propertyWithNoValue()

        given:
        property.set(brokenSupplier())
        property.attachOwner(owner(), displayName("<property>"))
        property.finalizeValueOnRead()

        when:
        property.finalizeValue()

        then:
        def e = thrown(AbstractProperty.PropertyQueryException)
        e.message == "Failed to calculate the value of <property>."
        e.cause.message == "broken!"

        when:
        property.present

        then:
        def e2 = thrown(AbstractProperty.PropertyQueryException)
        e2.message == "Failed to calculate the value of <property>."
        e2.cause.message == "broken!"

        when:
        property.get()

        then:
        def e3 = thrown(AbstractProperty.PropertyQueryException)
        e3.message == "Failed to calculate the value of <property>."
        e3.cause.message == "broken!"
    }

    def "does not finalize upstream property on finalize"() {
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()
        property.set(upstream)
        upstream.set(someValue())

        given:
        property.finalizeValue()

        when:
        upstream.set(someOtherValue())

        then:
        property.get() == someValue()
        upstream.get() == someOtherValue()
    }

    def "does not finalize upstream property with no value on finalize"() {
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()
        property.set(upstream)

        given:
        property.finalizeValue()

        when:
        upstream.set(someOtherValue())

        then:
        property.orNull == null
        upstream.get() == someOtherValue()
    }

    def "does not finalize mapped upstream property on finalize"() {
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()
        upstream.set(someValue())
        property.set(upstream.map { someOtherValue() })

        given:
        property.finalizeValue()

        when:
        upstream.set(someOtherValue2())

        then:
        property.get() == someOtherValue()
        upstream.get() == someOtherValue2()
    }

    def "does not finalize flatMapped upstream property on finalize"() {
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()
        upstream.set(someValue())
        property.set(upstream.flatMap { supplierWithValues(someOtherValue()) })

        given:
        property.finalizeValue()

        when:
        upstream.set(someOtherValue2())

        then:
        property.get() == someOtherValue()
        upstream.get() == someOtherValue2()
    }

    def "does not finalize orElse upstream property on finalize"() {
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()
        property.set(upstream.orElse(someValue()))

        given:
        property.finalizeValue()

        when:
        upstream.set(someOtherValue())

        then:
        property.get() == someValue()
        upstream.get() == someOtherValue()
    }

    def "does not finalize orElse upstream properties on finalize"() {
        def upstream1 = propertyWithNoValue()
        def upstream2 = propertyWithNoValue()
        def property = propertyWithNoValue()
        property.set(upstream1.orElse(upstream2))
        upstream2.set(someValue())

        given:
        property.finalizeValue()

        when:
        upstream1.set(someOtherValue())
        upstream2.set(someOtherValue())

        then:
        property.get() == someValue()
        upstream1.get() == someOtherValue()
        upstream2.get() == someOtherValue()
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

    def "can read value while finalizing property value"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)
        property.set(provider)

        // TODO - this verifies that a workaround to accidental concurrent access works. Instead, this should really be an error

        when:
        property.finalizeValue()

        then:
        1 * function.call() >> {
            property.get()
        }
        1 * function.call() >> someValue()
        0 * function._

        when:
        def result = property.get()

        then:
        result == someValue()
        0 * function._
    }

    def "can read value while finalizing property value on read when finalize on read enabled"() {
        def property = propertyWithNoValue()
        def function = Mock(Callable)
        def provider = new DefaultProvider<T>(function)
        property.set(provider)
        property.finalizeValueOnRead()

        // TODO - this verifies that a workaround to accidental concurrent access works. Instead, this should really be an error

        when:
        def result = property.get()

        then:
        result == someValue()
        1 * function.call() >> {
            property.get()
        }
        1 * function.call() >> someValue()
        0 * function._

        when:
        def result2 = property.get()

        then:
        result2 == someValue()
        0 * function._
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

        then:
        !present
        0 * _

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this property because it has no value available."
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

    def "finalized property with no value reports source of value when source is known"() {
        def a = propertyWithNoValue()
        a.attachOwner(owner(), displayName("<a>"))
        def b = propertyWithNoValue()
        b.attachOwner(owner(), displayName("<b>"))
        def property = propertyWithNoValue()

        given:
        property.set(b)
        b.set(a)

        and:
        property.finalizeValue()

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this property because it has no value available.
The value of this property is derived from:
  - <b>
  - <a>""")
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.setFromAnyValue(brokenSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.setFromAnyValue(brokenSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.setFromAnyValue(brokenSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.setFromAnyValue(brokenSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.setFromAnyValue(someValue())

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value after value finalized"() {
        given:
        def property = propertyWithNoValue()
        property.finalizeValue()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value after value finalized implicitly"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot set convention value after value queried and value finalized on read"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()
        property.get()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.convention(someValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> is final and cannot be changed any further.'
    }

    def "cannot set convention value after changes disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.disallowChanges()

        when:
        property.convention(someValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
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
        property.attachOwner(owner(), displayName("<display-name>"))
        property.convention(Mock(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display-name> cannot be changed any further.'
    }

    def "cannot read value until host is ready when unsafe read disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowUnsafeRead()

        when:
        property.get()

        then:
        1 * host.beforeRead(null) >> "<reason>"

        and:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of this property because <reason>."

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.get()

        then:
        1 * host.beforeRead(null) >> "<reason>"

        and:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot query the value of <display-name> because <reason>."

        when:
        def result = property.get()

        then:
        1 * host.beforeRead(null) >> null

        and:
        result == someValue()

        when:
        def result2 = property.get()

        then:
        0 * host._

        and:
        result2 == someValue()
    }

    def "cannot read value with upstream task output until host is ready and task completed when unsafe read disallowed"() {
        given:
        def producer = owner()
        def upstream = propertyWithNoValue()
        def property = propertyWithNoValue()

        upstream.set(someValue())
        upstream.attachOwner(owner(), displayName("<upstream>"))
        upstream.attachProducer(producer)

        property.set(upstream)
        property.attachOwner(owner(), displayName("<display-name>"))
        property.disallowUnsafeRead()

        when:
        property.get()

        then:
        1 * host.beforeRead(null) >> null
        1 * host.beforeRead(producer) >> "<task>"
        0 * host._

        and:
        def e = thrown(AbstractProperty.PropertyQueryException)
        e.message == "Failed to calculate the value of <display-name>."
        e.cause.message == "Cannot query the value of <upstream> because <task>."

        when:
        def result = property.get()

        then:
        1 * host.beforeRead(null) >> null
        1 * host.beforeRead(producer) >> null
        0 * host._

        and:
        result == someValue()

        when:
        def result2 = property.get()

        then:
        0 * host._

        and:
        result2 == someValue()
    }

    def "reports that value is unsafe to read regardless of whether a value is available or not"() {
        given:
        def property = propertyWithNoValue()
        property.disallowUnsafeRead()
        _ * host.beforeRead(null) >> "<reason>"

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of this property because <reason>."

        when:
        property.set(supplierWithNoValue())
        property.get()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot query the value of this property because <reason>."

        when:
        property.set(brokenSupplier())
        property.get()

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == "Cannot query the value of this property because <reason>."
    }

    def "cannot finalize a property when host is not ready and unsafe read disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.disallowUnsafeRead()
        property.set(someValue())

        when:
        property.finalizeValue()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot finalize the value of this property because <reason>."

        and:
        1 * host.beforeRead(null) >> "<reason>"
        0 * host._

        when:
        property.attachOwner(owner(), displayName("<property>"))
        property.finalizeValue()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "Cannot finalize the value of <property> because <reason>."

        and:
        1 * host.beforeRead(null) >> "<reason>"
        0 * host._

        when:
        def result = property.get()

        then:
        result == someValue()

        and:
        1 * host.beforeRead(null) >> null
        0 * host._

        when:
        property.finalizeValue()

        then:
        0 * host._
    }

    def "finalizes value after read when unsafe read disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.disallowUnsafeRead()

        when:
        property.convention(someOtherValue())
        property.set(brokenSupplier())
        setToNull(property)
        property.set(someValue())

        then:
        noExceptionThrown()

        when:
        def result = property.get()

        then:
        1 * host.beforeRead(null) >> null

        and:
        result == someValue()

        when:
        property.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == "The value for this property is final and cannot be changed any further."
    }

    def "can finalize on next read when host is not ready when unsafe read disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.disallowUnsafeRead()
        property.set(someValue())

        when:
        property.finalizeValueOnRead()

        then:
        0 * _

        when:
        def value = property.orNull

        then:
        value == someValue()

        and:
        1 * host.beforeRead(null) >> null
    }

    def "can disallow changes when host is not ready when unsafe read disallowed"() {
        given:
        def property = propertyWithNoValue()
        property.disallowUnsafeRead()
        property.set(someValue())

        when:
        property.disallowChanges()

        then:
        0 * _

        when:
        def value = property.orNull

        then:
        value == someValue()

        and:
        1 * host.beforeRead(null) >> null
    }

    def "finalizes upstream property when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b)
        property.disallowUnsafeRead()

        b.set(c)
        b.attachOwner(owner(), displayName("<b>"))

        c.set(someValue())
        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property."$method"()

        when:
        b.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    def "finalizes upstream property with no value when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b)
        property.disallowUnsafeRead()

        b.set(c)
        b.attachOwner(owner(), displayName("<b>"))

        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property."$method"()

        when:
        b.set(someOtherValue())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["getOrNull", "finalizeValue", "isPresent"]
    }

    def "finalizes upstream mapped property when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b.map { someOtherValue2() })
        property.disallowUnsafeRead()

        b.set(c.map { someOtherValue() })
        b.attachOwner(owner(), displayName("<b>"))

        c.set(someValue())
        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property."$method"()

        when:
        b.set(someOtherValue3())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue3())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    def "finalizes upstream flatmapped property when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b.flatMap { supplierWithValues(someOtherValue2()) })
        property.disallowUnsafeRead()

        b.set(c.flatMap { supplierWithValues(someOtherValue()) })
        b.attachOwner(owner(), displayName("<b>"))

        c.set(someValue())
        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property."$method"()

        when:
        b.set(someOtherValue3())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue3())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    def "finalizes upstream orElse fixed value property when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b.orElse(someOtherValue2()))
        property.disallowUnsafeRead()

        b.set(c)
        b.attachOwner(owner(), displayName("<b>"))

        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property.get()

        when:
        b.set(someOtherValue3())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue3())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    def "finalizes upstream orElse properties when value read using #method and unsafe read disallowed"() {
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        def property = propertyWithNoValue()

        property.set(b.orElse(c))
        property.disallowUnsafeRead()

        b.attachOwner(owner(), displayName("<b>"))

        c.set(someValue())
        c.attachOwner(owner(), displayName("<c>"))
        c.disallowUnsafeRead()

        given:
        property."$method"()

        when:
        b.set(someOtherValue3())

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set(someOtherValue3())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    def "reports the source of property value when value is missing and source is known"() {
        given:
        def a = propertyWithNoValue()
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        a.attachOwner(owner(), displayName("<a>"))
        a.set(b)
        b.set(c)

        when:
        a.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of <a> because it has no value available."

        when:
        c.attachOwner(owner(), displayName("<c>"))
        a.get()

        then:
        def e2 = thrown(MissingValueException)
        e2.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of <a> because it has no value available.
The value of this property is derived from: <c>""")

        when:
        b.attachOwner(owner(), displayName("<b>"))
        a.get()

        then:
        def e3 = thrown(MissingValueException)
        e3.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of <a> because it has no value available.
The value of this property is derived from:
  - <b>
  - <c>""")
    }

    def "reports the source of mapped property value when value is missing and source is known"() {
        given:
        def a = propertyWithNoValue()
        def b = propertyWithNoValue()
        def c = propertyWithNoValue()
        a.attachOwner(owner(), displayName("<a>"))
        a.set(b.map { it })
        b.set(c.map { it })
        def provider = a.map { it }

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from: <a>""")

        when:
        c.attachOwner(owner(), displayName("<c>"))
        provider.get()

        then:
        def e2 = thrown(MissingValueException)
        e2.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from:
  - <a>
  - <c>""")

        when:
        b.attachOwner(owner(), displayName("<b>"))
        provider.get()

        then:
        def e3 = thrown(MissingValueException)
        e3.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this provider because it has no value available.
The value of this provider is derived from:
  - <a>
  - <b>
  - <c>""")
    }

    def "has no producer task and missing execution time value when property has no value"() {
        def property = propertyWithNoValue()

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has no producer task and fixed execution time value when property has a fixed value"() {
        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == someValue()
    }

    def "can attach a producer task to property"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        property.set(someValue())

        expect:
        assertHasNoProducer(property)

        when:
        property.attachProducer(owner(task))

        then:
        assertHasProducer(property, task)

        when:
        setToNull(property)

        then:
        assertHasProducer(property, task)
    }

    def "producer task of upstream provider is ignored when producer task attached to property"() {
        def task = Mock(Task)
        def upstream = Mock(Task)
        def property = propertyWithNoValue()
        property.set(supplierWithProducer(upstream))

        expect:
        assertHasProducer(property, upstream)

        when:
        property.attachProducer(owner(task))

        then:
        assertHasProducer(property, task)
    }

    def "has no producer task and missing execution time value when value is provider with no value"() {
        def provider = supplierWithNoValue()
        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has producer task and fixed execution time value when value is provider with producer task and fixed value"() {
        def task = Stub(Task)
        def provider = supplierWithProducer(task, someValue())
        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        assertHasProducer(property, task)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.hasChangingContent()
        value.getFixedValue() == someValue()
    }

    def "has producer task and changing execution time value when value is provider with producer task and changing value"() {
        def task = Stub(Task)
        def provider = supplierWithProducerAndChangingExecutionTimeValue(task, someValue(), someOtherValue())
        def property = propertyWithNoValue()
        property.set(provider)

        expect:
        assertHasProducer(property, task)
        def value = property.calculateExecutionTimeValue()
        value.isChangingValue()
        value.hasChangingContent()
        value.getChangingValue().get() == someValue()
        value.getChangingValue().get() == someOtherValue()
    }

    def "can calculate execution time value without finalizing when finalize on read is enabled"() {
        def property = propertyWithNoValue()
        property.set(someValue())
        property.finalizeValueOnRead()

        expect:
        property.calculateExecutionTimeValue().getFixedValue() == someValue()
        property.set(someOtherValue())
        property.calculateExecutionTimeValue().getFixedValue() == someOtherValue()
        property.get() == someOtherValue()
    }

    def "can calculate execution time value without finalizing when unsafe read disallowed"() {
        def property = propertyWithNoValue()
        property.set(someValue())
        property.disallowUnsafeRead()

        expect:
        property.calculateExecutionTimeValue().getFixedValue() == someValue()
        property.set(someOtherValue())
        property.calculateExecutionTimeValue().getFixedValue() == someOtherValue()
        property.get() == someOtherValue()
    }

    def "mapped value has changing execution time value when producer task attached to original property"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        property.set(someValue())
        def mapped = property.map { someOtherValue() }

        expect:
        assertHasNoProducer(mapped)
        def value = mapped.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == someOtherValue()

        property.attachProducer(owner(task))

        assertHasProducer(mapped, task)
        def value2 = mapped.calculateExecutionTimeValue()
        value2.isChangingValue()
        value2.getChangingValue().get() == someOtherValue()
    }

    def "mapped value has no execution time value when producer task attached to original property with no value"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        setToNull(property)
        def mapped = property.map { it }

        expect:
        assertHasNoProducer(mapped)
        mapped.calculateExecutionTimeValue().isMissing()

        property.attachProducer(owner(task))

        assertHasProducer(mapped, task)
        mapped.calculateExecutionTimeValue().isMissing()
    }

    def "chain of mapped value has value producer when producer task attached to original property"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        property.set(someValue())
        def mapped = property.map { it }.map { it }.map { someOtherValue() }

        expect:
        assertHasNoProducer(mapped)
        def value = mapped.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == someOtherValue()

        property.attachProducer(owner(task))

        assertHasProducer(mapped, task)
        def value2 = mapped.calculateExecutionTimeValue()
        value2.isChangingValue()
        value2.getChangingValue().get() == someOtherValue()
    }

    def "mapped value has value producer when value is provider with content producer"() {
        def task = Mock(Task)
        def provider = supplierWithProducer(task, someValue())

        def property = propertyWithNoValue()
        property.set(provider)
        def mapped = property.map { someOtherValue() }

        expect:
        assertHasProducer(mapped, task)
        def value = mapped.calculateExecutionTimeValue()
        value.isChangingValue()
        value.getChangingValue().get() == someOtherValue()
    }

    def "chain of mapped and filtered value has value producer when producer task attached to original property"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        property.set(someValue())
        def mapped = property.map { it }.filter { true }.map { someOtherValue() }

        expect:
        assertHasNoProducer(mapped)
        def value = mapped.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == someOtherValue()

        property.attachProducer(owner(task))

        assertHasProducer(mapped, task)
        def value2 = mapped.calculateExecutionTimeValue()
        value2.isChangingValue()
        value2.getChangingValue().get() == someOtherValue()
    }

    def "filtered value has no execution time value when producer task attached to original property with no value"() {
        def task = Mock(Task)
        def property = propertyWithNoValue()
        def filtered = property.filter { true }

        expect:
        assertHasNoProducer(filtered)
        filtered.calculateExecutionTimeValue().isMissing()

        property.attachProducer(owner(task))

        assertHasProducer(filtered, task)
        filtered.calculateExecutionTimeValue().isMissing()
    }

    def "fails when property has multiple producers attached"() {
        def owner1 = owner()
        owner1.modelIdentityDisplayName >> displayName("<owner 1>")
        def owner2 = owner()
        owner2.modelIdentityDisplayName >> displayName("<owner 2>")

        given:
        def property = propertyWithNoValue()
        property.attachProducer(owner1)

        when:
        property.attachProducer(owner2)

        then:
        def e = thrown(IllegalStateException)
        e.message == "This property is already declared as an output property of <owner 1> (type ${owner1.class.simpleName}). Cannot also declare it as an output property of <owner 2> (type ${owner2.class.simpleName})."

        when:
        property.attachOwner(owner(), displayName("<display-name>"))
        property.attachProducer(owner2)

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == "<display-name> is already declared as an output property of <owner 1> (type ${owner1.class.simpleName}). Cannot also declare it as an output property of <owner 2> (type ${owner2.class.simpleName})."
    }

    def "fails when property has producer with no task"() {
        def owner = owner()
        owner.taskThatOwnsThisObject >> null
        owner.modelIdentityDisplayName >> displayName("<owner>")

        given:
        def property = propertyWithNoValue()
        property.attachProducer(owner)

        when:
        property.producer.visitProducerTasks(Stub(Action))

        then:
        def e = thrown(IllegalStateException)
        e.message == "This property is declared as an output property of <owner> (type ${owner.class.simpleName}) but does not have a task associated with it."
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

    def "runs side effect when calling '#getter' when empty property value is set via '#setter'"() {
        given:
        def property = providerWithNoValue()

        when:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def providerWithSideEffect = Providers.of(someValue()).withSideEffect(sideEffect)
        // `PropertyInternal` does not directly provide these setters,
        // but all user-facing interfaces and their implementations do.
        property."$setter"(providerWithSideEffect)

        property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        property.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when calling setter or calculated values are not unpacked

        when:
        def unpackedValue = getter(property, getter, someOtherValue())

        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        where:
        setter  | getter
        "set"   | "get"
        "set"   | "getOrNull"
        "set"   | "getOrElse"
        "value" | "get"
        "value" | "getOrNull"
        "value" | "getOrElse"
    }

    def "runs side effect when calling '#getter' when property's value is overridden via '#setter'"() {
        given:
        def property = providerWithValue(someOtherValue())

        when:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def providerWithSideEffect = Providers.of(someValue()).withSideEffect(sideEffect)
        // `PropertyInternal` does not directly provide these setters,
        // but all user-facing interfaces and their implementations do.
        property."$setter"(providerWithSideEffect)

        property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        property.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when calling setter or calculated values are not unpacked

        when:
        def unpackedValue = getter(property, getter, someOtherValue2())

        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        where:
        setter  | getter
        "set"   | "get"
        "set"   | "getOrNull"
        "set"   | "getOrElse"
        "value" | "get"
        "value" | "getOrNull"
        "value" | "getOrElse"
    }

    def "runs side effect when calling '#getter' on default property with convention with side effect"() {
        given:
        def property = providerWithNoValue()
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def providerWithSideEffect = Providers.of(someValue()).withSideEffect(sideEffect)

        when:
        // `PropertyInternal` does not directly provide `convention` method,
        // but all user-facing interfaces and their implementations do.
        property.convention(providerWithSideEffect)

        property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        property.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when setting convention or calculated values are not unpacked

        when:
        def unpackedValue = getter(property, getter, someOtherValue())

        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        where:
        getter      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    def "does not run side effect from convention when calling '#getter' when property's value is explicitly set"() {
        given:
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def providerWithSideEffect = Providers.of(someValue()).withSideEffect(sideEffect)
        def property = providerWithNoValue()

        when:
        // `PropertyInternal` does not directly provide `convention` nor setter methods,
        // but all user-facing interfaces and their implementations do.
        property.convention(providerWithSideEffect)
        property."$setter"(someOtherValue())

        property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        property.calculateExecutionTimeValue()

        then:
        0 * _ // no side effects when calling convention, setter or calculated values are not unpacked

        when:
        def unpackedValue = getter(property, getter, someOtherValue2())

        then:
        unpackedValue == someOtherValue()
        0 * _

        where:
        setter  | getter
        "set"   | "get"
        "set"   | "getOrNull"
        "set"   | "getOrElse"
        "value" | "get"
        "value" | "getOrNull"
        "value" | "getOrElse"
    }

    ModelObject owner() {
        return Stub(ModelObject)
    }

    ModelObject owner(Task task) {
        def owner = Stub(ModelObject)
        _ * owner.taskThatOwnsThisObject >> task
        return owner
    }

    DisplayName displayName(String name) {
        return Describables.of(name)
    }

    /**
     * A dummy provider with no value.
     */
    ProviderInternal<T> supplierWithNoValue() {
        return new NoValueProvider<T>(type(), null)
    }

    /**
     * A dummy provider with no value and the given display name
     */
    ProviderInternal<T> supplierWithNoValue(DisplayName displayName) {
        return new NoValueProvider<T>(type(), displayName)
    }

    /**
     * A dummy provider with no value and the given display name
     */
    ProviderInternal<T> supplierWithNoValue(Class type, DisplayName displayName) {
        return new NoValueProvider<T>(type, displayName)
    }

    /**
     * A dummy provider that provides one of given values each time it is queried, in the order given.
     */
    ProviderInternal<T> supplierWithValues(T... values) {
        return ProviderTestUtil.withValues(values)
    }

    ProviderInternal<T> supplierWithChangingExecutionTimeValues(T... values) {
        return ProviderTestUtil.withChangingExecutionTimeValues(values)
    }

    ProviderInternal<T> supplierWithProducer(Task producer, T... values) {
        return ProviderTestUtil.withProducer(type(), producer, values)
    }

    ProviderInternal<T> supplierWithProducerAndChangingExecutionTimeValue(Task producer, T... values) {
        return ProviderTestUtil.withProducerAndChangingExecutionTimeValue(type(), producer, values)
    }

    class NoValueProvider<T> extends AbstractMinimalProvider<T> {
        private final Class<T> type
        private final DisplayName displayName

        NoValueProvider(Class<T> type, DisplayName displayName) {
            this.displayName = displayName
            this.type = type
        }

        @Override
        Class<T> getType() {
            return type
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return Value.missing()
        }

        @Override
        protected DisplayName getDeclaredDisplayName() {
            return displayName
        }
    }

    static class Thing {}
}
