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

import com.google.common.collect.ImmutableCollection
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.Describables
import org.gradle.internal.evaluation.CircularEvaluationException
import org.gradle.util.internal.TextUtil
import org.spockframework.lang.Wildcard

import java.util.function.Consumer

import static org.gradle.api.internal.provider.CircularEvaluationSpec.ProviderConsumer.GET_PRODUCER
import static org.gradle.api.internal.provider.Providers.notDefined

abstract class CollectionPropertySpec<C extends Collection<String>> extends PropertySpec<C> {
    AbstractCollectionProperty<String, C> propertyWithDefaultValue() {
        return property()
    }

    @Override
    AbstractCollectionProperty<String, C> propertyWithNoValue() {
        def p = property()
        p.set((List) null)
        return p
    }

    @Override
    C someValue() {
        return toMutable(["s1", "s2"])
    }

    @Override
    C someOtherValue() {
        return toMutable(["s1"])
    }

    @Override
    C someOtherValue2() {
        return toMutable(["s2"])
    }

    @Override
    C someOtherValue3() {
        return toMutable(["s3"])
    }

    abstract AbstractCollectionProperty<String, C> property()

    abstract String getCollectionName()

    @Override
    protected void setToNull(Object property) {
        property.set((Iterable) null)
    }

    @Override
    protected void nullConvention(Object property) {
        property.convention((Iterable) null)
    }

    def property = property()

    protected void assertValueIs(C expected, PropertyInternal<?> property = this.property) {
        assertPropertyValueIs(expected, property)
    }

    protected void assertEqualValues(C expected, C actual) {
        assert actual instanceof ImmutableCollection
        assert immutableCollectionType.isInstance(actual)
        assertCollectionIs(actual, expected)
    }

    protected void assertCollectionIs(ImmutableCollection actual, Collection<String> expected) {
        assert actual == toImmutable(expected)
        actual.each {
            assert it instanceof String
        }
    }

    protected abstract C toImmutable(Collection<String> values)

    protected abstract C toMutable(Collection<String> values)

    protected abstract Class<? extends ImmutableCollection<?>> getImmutableCollectionType()

    def "has empty collection as value by default"() {
        expect:
        assertValueIs([])
        !property.explicit
    }

    def "can change value to empty collection"() {
        property.set(["abc"])
        property.empty()

        expect:
        assertValueIs([])
        property.explicit
    }

    def "can set value using empty collection"() {
        expect:
        property.set(toMutable([]))
        assertValueIs([])
    }

    def "returns immutable copy of value"() {
        expect:
        property.set(toMutable(["abc"]))
        assertValueIs(["abc"])
    }

    def "can set value from various collection types"() {
        def iterable = Stub(Iterable)
        iterable.iterator() >> ["4", "5"].iterator()

        expect:
        property.set(["1", "2"])
        property.get() == toImmutable(["1", "2"])

        property.set(["2", "3"] as Set)
        property.get() == toImmutable(["2", "3"])

        property.set(iterable)
        property.get() == toImmutable(["4", "5"])
    }

    def "can set string property from collection containing GString"() {
        expect:
        property.set(["${'321'.substring(2)}"])
        assertValueIs(["1"])
    }

    def "can set untyped from various collection types"() {
        def iterable = Stub(Iterable)
        iterable.iterator() >> ["4", "5"].iterator()

        expect:
        property.setFromAnyValue(["1", "2"])
        property.get() == toImmutable(["1", "2"])

        property.setFromAnyValue(["2", "3"] as Set)
        property.get() == toImmutable(["2", "3"])

        property.setFromAnyValue(iterable)
        property.get() == toImmutable(["4", "5"])
    }

    def "can set untyped from provider"() {
        def provider = Stub(ProviderInternal)
        provider.type >> null
        provider.calculateValue(_) >>> [["1"], ["2"]].collect { ValueSupplier.Value.of(it) }

        expect:
        property.setFromAnyValue(provider)
        property.get() == toImmutable(["1"])
        property.get() == toImmutable(["2"])
    }

    def "can set string property from provider that returns collection containing GString"() {
        def provider = Stub(Provider)
        def value = ["${'321'.substring(2)}"]
        provider.get() >>> value

        expect:
        property.set(value)
        assertValueIs(["1"])
    }

    def "queries initial value for every call to get()"() {
        expect:
        def initialValue = toMutable(["abc"])
        property.set(initialValue)
        assertValueIs(["abc"])
        initialValue.add("added")
        assertValueIs(["abc", "added"])
    }

    def "queries underlying provider for every call to get()"() {
        def provider = Stub(ProviderInternal)
        provider.type >> List
        provider.calculateValue(_) >>> [["123"], ["abc"]].collect { ValueSupplier.Value.of(it) }
        provider.calculatePresence(_) >> true

        expect:
        property.set(provider)
        assertValueIs(["123"])
        assertValueIs(["abc"])
    }

    def "mapped provider is presented with immutable copy of value"() {
        given:
        property.set(toMutable(["abc"]))
        def provider = property.map(new Transformer() {
            def transform(def value) {
                assert immutableCollectionType.isInstance(value)
                assert value == toImmutable(["abc"])
                return toMutable(["123"])
            }
        })

        expect:
        def actual = provider.get()
        actual == toMutable(["123"])
    }

    def "appends a single value using add"() {
        given:
        property.set(toMutable(["abc"]))
        property.add("123")
        property.add("456")

        expect:
        assertValueIs(["abc", "123", "456"])
    }

    def "appends a single value to string property using GString"() {
        given:
        property.set(toMutable(["abc"]))
        property.add("${'321'.substring(2)}")

        expect:
        assertValueIs(["abc", "1"])
    }

    def "appends a single value from provider using add"() {
        given:
        property.set(toMutable(["abc"]))
        property.add(Providers.of("123"))
        property.add(Providers.of("456"))

        expect:
        assertValueIs(["abc", "123", "456"])
    }

    def "appends a single value to string property from provider with GString value using add"() {
        given:
        property.set(toMutable(["abc"]))
        property.add(Providers.of("${'321'.substring(2)}"))

        expect:
        assertValueIs(["abc", "1"])
    }

    def "appends zero or more values from array #value using addAll"() {
        given:
        property.addAll(value as String[])

        expect:
        assertValueIs(expectedValue)

        where:
        value                 | expectedValue
        []                    | []
        ["aaa"]               | ["aaa"]
        ["aaa", "bbb", "ccc"] | ["aaa", "bbb", "ccc"]
    }

    def "appends value to string property from array with GString value using addAll"() {
        given:
        property.set(toMutable(["abc"]))
        property.addAll("${'321'.substring(2)}")

        expect:
        assertValueIs(["abc", "1"])
    }

    def "appends zero or more values from provider #value using addAll"() {
        given:
        property.addAll(Providers.of(value))

        expect:
        assertValueIs(expectedValue)

        where:
        value                 | expectedValue
        []                    | []
        ["aaa"]               | ["aaa"]
        ["aaa", "bbb", "ccc"] | ["aaa", "bbb", "ccc"]
    }

    def "queries values of provider on every call to get()"() {
        def provider = Stub(ProviderInternal)
        _ * provider.calculatePresence(_) >> true
        _ * provider.calculateValue(_) >>> [["abc"], ["def"]].collect { ValueSupplier.Value.of(it) }

        expect:
        property.addAll(provider)
        assertValueIs(["abc"])
        assertValueIs(["def"])
    }

    def "appends value to string property from provider with GString value using addAll"() {
        given:
        property.set(toMutable(["abc"]))
        property.addAll(Providers.of(["${'321'.substring(2)}"]))

        expect:
        assertValueIs(["abc", "1"])
    }

    def "appends zero or more values from collection #value using addAll"() {
        given:
        property.addAll(value)

        expect:
        assertValueIs(expectedValue)

        where:
        value                 | expectedValue
        []                    | []
        ["aaa"]               | ["aaa"]
        ["aaa", "bbb", "ccc"] | ["aaa", "bbb", "ccc"]
    }

    def "queries values of collection on every call to get()"() {
        expect:
        def value = ["abc"]
        property.addAll(value)
        assertValueIs(["abc"])
        value.add("added")
        assertValueIs(["abc", "added"])
    }

    def "appends value to string property from collection with GString value using addAll"() {
        given:
        property.set(toMutable(["abc"]))
        property.addAll(["${'321'.substring(2)}"])

        expect:
        assertValueIs(["abc", "1"])
    }

    def "providers only called once per query"() {
        def valueProvider = Mock(ProviderInternal)
        def addProvider = Mock(ProviderInternal)
        def addAllProvider = Mock(ProviderInternal)

        given:
        property.set(valueProvider)
        property.add(addProvider)
        property.addAll(addAllProvider)

        when:
        property.present

        then:
        1 * valueProvider.calculatePresence(_) >> true
        1 * addProvider.calculatePresence(_) >> true
        1 * addAllProvider.calculatePresence(_) >> true
        0 * _

        when:
        property.get()

        then:
        1 * valueProvider.calculateValue(_) >> ValueSupplier.Value.of(["1"])
        1 * addProvider.calculateValue(_) >> ValueSupplier.Value.of("2")
        1 * addAllProvider.calculateValue(_) >> ValueSupplier.Value.of(["3"])
        0 * _

        when:
        property.getOrNull()

        then:
        1 * valueProvider.calculateValue(_) >> ValueSupplier.Value.of(["1"])
        1 * addProvider.calculateValue(_) >> ValueSupplier.Value.of("2")
        1 * addAllProvider.calculateValue(_) >> ValueSupplier.Value.of(["3"])
        0 * _
    }

    def "can append values to empty property"() {
        given:
        property.add("1")
        property.add(Providers.of("2"))
        property.addAll(["3"])
        property.addAll(Providers.of(["4"]))

        expect:
        assertValueIs(["1", "2", "3", "4"])
    }

    def "empty collection is used as value when elements added after convention set"() {
        given:
        property.convention(["1", "2"])
        property.add("3")

        expect:
        assertValueIs(["3"])
    }

    def "property has no value when set to null and other values appended"() {
        given:
        property.set((Iterable) null)
        property.add("1")
        property.add(Providers.of("2"))
        property.addAll(["3"])
        property.addAll(Providers.of(["4"]))

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "property has no value when set to provider with no value and other values appended"() {
        given:
        property.set(notDefined())

        and:
        property.add("1")
        property.add(Providers.of("2"))
        property.addAll(["3"])
        property.addAll(Providers.of(["4"]))

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "property has no value when adding an element provider with no value"() {
        given:
        property.set(toMutable(["123"]))
        property.add("456")
        property.add(notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "reports the source of element provider when value is missing and source is known"() {
        given:
        def elementProvider = supplierWithNoValue(Describables.of("<source>"))
        property.set(toMutable(["123"]))
        property.add(elementProvider)

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of ${displayName} because it has no value available.
The value of this property is derived from: <source>""")
    }

    def "property has no value when adding an collection provider with no value"() {
        given:
        property.set(toMutable(["123"]))
        property.add("456")
        property.addAll(notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(toMutable(["other"])) == toMutable(["other"])

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "reports the source of collection provider when value is missing and source is known"() {
        given:
        def elementsProvider = supplierWithNoValue(Describables.of("<source>"))
        property.set(toMutable(["123"]))
        property.addAll(elementsProvider)

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of ${displayName} because it has no value available.
The value of this property is derived from: <source>""")
    }

    def "can set to null value to discard value"() {
        given:
        def property = property()
        property.set(someValue())
        property.set((Iterable) null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can set null value to remove any added values"() {
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll(Providers.of(["hij"]))

        property.set((Iterable) null)

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can set value to replace added values"() {
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll("ghi")
        property.addAll(["jkl"])
        property.addAll(Providers.of(["mno"]))

        expect:
        property.set(toMutable(["123", "456"]))
        assertValueIs(["123", "456"])
    }

    def "can make empty to replace added values"() {
        property.add("abc")
        property.add(Providers.of("def"))
        property.addAll("ghi")
        property.addAll(["jkl"])
        property.addAll(Providers.of(["mno"]))

        expect:
        property.empty()
        assertValueIs([])
    }

    def "throws NullPointerException when provider returns list with null to property"() {
        given:
        property.addAll(Providers.of([null]))

        when:
        property.get()

        then:
        def ex = thrown(NullPointerException)
    }

    def "throws NullPointerException when adding a null value to the property"() {
        when:
        property.add(null)

        then:
        def ex = thrown(NullPointerException)
        ex.message == "Cannot add a null element to a property of type ${type().simpleName}."
    }

    def "ignores convention after element added"() {
        expect:
        property.add("a")
        property.convention(["other"])
        assertValueIs(["a"])
    }

    def "ignores convention after element added using provider"() {
        expect:
        property.add(Providers.of("a"))
        property.convention(["other"])
        assertValueIs(["a"])
    }

    def "ignores convention after elements added"() {
        expect:
        property.addAll(["a", "b"])
        property.convention(["other"])
        assertValueIs(["a", "b"])
    }

    def "ignores convention after elements added using provider"() {
        expect:
        property.addAll(Providers.of(["a", "b"]))
        property.convention(["other"])
        assertValueIs(["a", "b"])
    }

    def "ignores convention after collection made empty"() {
        expect:
        property.empty()
        property.convention(["other"])
        assertValueIs([])
    }

    def "has no producer and fixed execution time value by default"() {
        expect:
        assertHasKnownProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue().isEmpty()
    }

    def "has no producer and missing execution time value when element provider with no value added"() {
        given:
        property.addAll("a", "b")
        property.add(supplierWithNoValue())
        property.add("c")
        property.add(supplierWithValues("d"))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has no producer and missing execution time value when elements provider with no value added"() {
        given:
        property.addAll("a", "b")
        property.add(supplierWithValues("d"))
        property.add("c")
        property.addAll(supplierWithNoValue())

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has no producer and fixed execution time value when element added"() {
        given:
        property.add("a")
        property.add("b")

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == toImmutable(["a", "b"])
    }

    def "has no producer and fixed execution time value when elements added"() {
        given:
        property.addAll("a", "b")
        property.addAll(["c"])

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == toImmutable(["a", "b", "c"])
    }

    def "has no producer and fixed execution time value when element provider added"() {
        given:
        property.add(supplierWithValues("a"))
        property.add(supplierWithValues("b"))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == toImmutable(["a", "b"])
    }

    def "has no producer and fixed execution time value when elements provider added"() {
        given:
        property.addAll(supplierWithValues(["a", "b"]))
        property.addAll(supplierWithValues(["c"]))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        value.fixedValue == toImmutable(["a", "b", "c"])
    }

    def "has no producer and changing execution time value when elements provider with changing value added"() {
        given:
        property.addAll(supplierWithChangingExecutionTimeValues(["a", "b"], ["a"]))
        property.addAll(supplierWithValues(["c"]))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isChangingValue()
        value.getChangingValue().get() == toImmutable(["a", "b", "c"])
        value.getChangingValue().get() == toImmutable(["a", "c"])
    }

    def "has union of producer task from providers unless producer task attached"() {
        given:
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def producer = Stub(Task)
        property.set(supplierWithProducer(task1))
        property.addAll(supplierWithProducer(task2))
        property.add(supplierWithProducer(task3))

        expect:
        assertHasProducer(property, task1, task2, task3)

        property.attachProducer(owner(producer))
        assertHasProducer(property, producer)
    }

    def "cannot set to empty list after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.empty()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set to empty list after value finalized implicitly"() {
        given:
        def property = property()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.empty()


        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set to empty list after changes disallowed"() {
        given:
        def property = property()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.empty()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add element after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.add("123")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.add(Stub(PropertyInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot add element after value finalized implicitly"() {
        given:
        def property = property()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.add("123")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.add(Stub(PropertyInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add element after changes disallowed"() {
        given:
        def property = property()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.add("123")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.add(Stub(PropertyInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add elements after value finalized"() {
        given:
        def property = property()
        property.set(someValue())
        property.finalizeValue()

        when:
        property.addAll("123", "456")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.addAll(["123", "456"])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.addAll(Stub(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot add elements after value finalized implicitly"() {
        given:
        def property = property()
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.addAll("123", "456")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.addAll(["123", "456"])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.addAll(Stub(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add elements after changes disallowed"() {
        given:
        def property = property()
        property.set(someValue())
        property.disallowChanges()

        when:
        property.addAll("123", "456")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.addAll(["123", "456"])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'

        when:
        property.addAll(Stub(ProviderInternal))

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for this property cannot be changed any further.'
    }

    def "finalizes upstream properties when value read using #method and disallow unsafe reads"() {
        def a = property()
        def b = property()
        def c = elementProperty()
        def property = property()
        property.disallowUnsafeRead()

        property.addAll(a)

        a.addAll(b)
        a.attachOwner(owner(), displayName("<a>"))

        b.attachOwner(owner(), displayName("<b>"))

        property.add(c)
        c.set("c")
        c.attachOwner(owner(), displayName("<c>"))

        given:
        property."$method"()

        when:
        a.set(['a'])

        then:
        def e1 = thrown(IllegalStateException)
        e1.message == 'The value for <a> is final and cannot be changed any further.'

        when:
        b.set(['a'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <b> is final and cannot be changed any further.'

        when:
        c.set('c2')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <c> is final and cannot be changed any further.'

        where:
        method << ["get", "finalizeValue", "isPresent"]
    }

    Property<String> elementProperty() {
        return new DefaultProperty<String>(host, String)
    }

    def "runs side effect when calling '#getter' on property to which providers were added via 'add'"() {
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def expectedUnpackedValue = ["some value", "simple value", "other value"]

        when:
        property.add(Providers.of("some value").withSideEffect(sideEffect1))
        property.add(Providers.of("simple value"))
        property.add(Providers.of("other value").withSideEffect(sideEffect2))

        def value = property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        def executionTimeValue = property.calculateExecutionTimeValue()
        then:
        0 * _ // no side effects until values are unpacked

        when:
        def unpackedValue = value.get()
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect1.execute("some value")
        then: // ensure ordering
        1 * sideEffect2.execute("other value")
        0 * _

        when:
        unpackedValue = executionTimeValue.toValue().get()
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect1.execute("some value")
        then: // ensure ordering
        1 * sideEffect2.execute("other value")
        0 * _

        when:
        unpackedValue = getter(property, getter, toMutable(["yet another value"]))
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect1.execute("some value")
        then: // ensure ordering
        1 * sideEffect2.execute("other value")
        0 * _

        where:
        getter      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    def "runs side effect when calling '#getter' on property to which providers were added via 'addAll'"() {
        def sideEffect = Mock(ValueSupplier.SideEffect)
        def expectedUnpackedValue = ["some value", "other value"]

        when:
        property.addAll(Providers.of(["some value", "other value"]).withSideEffect(sideEffect))

        def value = property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        def executionTimeValue = property.calculateExecutionTimeValue()
        then:
        0 * _ // no side effects until values are unpacked

        when:
        def unpackedValue = value.get()
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect.execute(expectedUnpackedValue)
        0 * _

        when:
        unpackedValue = executionTimeValue.toValue().get()
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect.execute(expectedUnpackedValue)
        0 * _

        when:
        unpackedValue = getter(property, getter, toMutable(["yet another value"]))
        then:
        unpackedValue == toImmutable(expectedUnpackedValue)
        1 * sideEffect.execute(expectedUnpackedValue)
        0 * _

        where:
        getter      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }

    def "does not run side effect when calling 'size'"() {
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)

        when:
        property.add(Providers.of("some value").withSideEffect(sideEffect1))
        property.addAll(Providers.of(["other value"]).withSideEffect(sideEffect2))
        property.size()

        then:
        0 * _
    }

    def "replace can modify property"() {
        given:
        property.set(someValue())

        when:
        property.replace { it.map { someOtherValue() } }

        then:
        property.get() == someOtherValue()
    }

    def "replace can modify property with convention"() {
        given:
        property.convention(someValue())

        when:
        property.replace { it.map { someOtherValue() } }

        then:
        property.get() == someOtherValue()
    }

    def "replace is not applied to later property modifications"() {
        given:
        property.set(someValue())

        when:
        property.replace { it.map { v -> v.collect { s -> s.reverse() } } }
        property.set(someOtherValue())

        then:
        property.get() == someOtherValue()
    }

    def "replace argument is live"() {
        given:
        def upstream = property().value(someValue()) as AbstractCollectionProperty<String, C>
        property.set(upstream)

        when:
        property.replace { it.map { v -> v.collect { s -> s.reverse() } } }
        upstream.set(someOtherValue())

        then:
        property.get() as Set<String> == someOtherValue().collect { it.reverse() } as Set<String>
    }

    def "returning null from replace unsets the property"() {
        given:
        property.set(someValue())

        when:
        property.replace { null }

        then:
        !property.isPresent()
    }

    def "returning null from replace unsets the property falling back to convention"() {
        given:
        property.value(someValue()).convention(someOtherValue())

        when:
        property.replace { null }

        then:
        property.get() == someOtherValue()
    }

    def "replace transformation runs eagerly"() {
        given:
        Transformer<Provider<String>, Provider<String>> transform = Mock()
        property.set(someValue())

        when:
        property.replace(transform)

        then:
        1 * transform.transform(_)
    }

    static abstract class CollectionPropertyCircularChainEvaluationTest<T, C extends Collection<T>> extends PropertySpec.PropertyCircularChainEvaluationSpec<C> {
        @Override
        abstract AbstractCollectionProperty<T, C> property()

        def "calling #consumer throws exception if added item provider references the property"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<T> item = property.map { list -> list.iterator().next() }
            property.add(item)

            when:
            consumer.accept(property)

            then:
            thrown(CircularEvaluationException)

            where:
            consumer << throwingConsumers()
        }

        def "calling #consumer throws exception if added item provider references the property and discards producer"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<T> item = property.map { list -> list.iterator().next() }
            property.add(new ProducerDiscardingProvider(item))

            when:
            consumer.accept(property)

            then:
            thrown(CircularEvaluationException)

            where:
            consumer << throwingConsumers() - [GET_PRODUCER]
        }

        def "calling #consumer is safe even if added item provider references the property"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<T> item = property.map { items -> items.iterator().next() }
            property.add(item)

            when:
            consumer.accept(property)

            then:
            noExceptionThrown()

            where:
            consumer << safeConsumers()
        }

        def "calling #consumer throws exception if added collection provider references the property"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<C> items = property.map { it }
            property.addAll(items)

            when:
            consumer.accept(property)

            then:
            thrown(CircularEvaluationException)

            where:
            consumer << throwingConsumers()
        }

        def "calling #consumer throws exception if added collection provider references the property and discards producer"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<C> items = property.map { it }
            property.addAll(new ProducerDiscardingProvider(items))

            when:
            consumer.accept(property)

            then:
            thrown(CircularEvaluationException)

            where:
            consumer << throwingConsumers() - [GET_PRODUCER]
        }

        def "calling #consumer is safe even if added collection provider references the property"(
            Consumer<ProviderInternal<?>> consumer
        ) {
            given:
            def property = property()
            Provider<C> itemsConcat = property.map { it }
            property.addAll(itemsConcat)

            when:
            consumer.accept(property)

            then:
            noExceptionThrown()

            where:
            consumer << safeConsumers()
        }
    }

    def "can add to convention value"() {
        given:
        property.convention(Providers.of(["1"]))
        property.withActualValue {
            it.add(Providers.of("2"))
            it.addAll(Providers.of(["3", "4"]))
        }

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit

        when:
        property.unset()

        then:
        assertValueIs toImmutable(["1"])
        !property.explicit
    }

    def "can add to convention value with append"() {
        given:
        property.convention(Providers.of(["1"]))
        property.append(Providers.of("2"))
        property.appendAll(Providers.of(["3", "4"]))

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit

        when:
        property.unset()

        then:
        assertValueIs toImmutable(["1"])
        !property.explicit
    }

    def "can add to explicit value"() {
        given:
        property.set([])
        property.withActualValue {
            it.addAll(Providers.of(["1", "2"]))
            it.addAll(Providers.of(["3", "4"]))
        }

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit
    }

    def "can add to explicit value with append"() {
        given:
        property.set([])
        property.appendAll(Providers.of(["1", "2"]))
        property.appendAll(Providers.of(["3", "4"]))

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit
    }

    def "can add to actual value without previous configuration"() {
        given:
        property.withActualValue {
            it.addAll(Providers.of(["1", "2"]))
            it.addAll(Providers.of(["3", "4"]))
        }

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit

        when:
        property.convention(Providers.of("0"))

        then:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit
    }

    def "can add to actual value without previous configuration with append"() {
        given:
        property.appendAll(Providers.of(["1", "2"]))
        property.appendAll(Providers.of(["3", "4"]))

        expect:
        assertValueIs toImmutable(["1", "2", "3", "4"])
        property.explicit
    }

    def "test '#label' vs undefined-safety"(String label) {
        given:
        if (!(convention instanceof Wildcard)) {
            if (convention instanceof Provider) {
                property.convention((Provider) convention)
            } else {
                property.convention((Iterable) convention)
            }
        }
        if (!(explicit instanceof Wildcard)) {
            if (explicit instanceof Provider) {
                property.set((Provider) explicit)
            } else {
                property.set((Iterable) explicit)
            }
        }

        when:
        operations.each {operation -> operation.call(property) }

        then:
        expected == null || property.getOrNull() == toImmutable(expected)
        expected != null || !property.present

        where:
        expected    | explicit      | convention    | label                                             | operations
        ["1"]       | _             | _             | "add"                                             | { it.add("1") }
        ["1"]       | _             | _             | "append"                                          | { it.append("1") }
        ["1"]       | []            | _             | "add to empty"                                    | { it.add("1") }
        ["1"]       | []            | _             | "append to empty"                                 | { it.append("1") }
        ["1"]       | _             | []            | "add to empty convention"                         | { it.add("1") }
        ["1"]       | _             | []            | "append to empty convention"                      | { it.append("1") }
        null        | null          | []            | "add to unset value w/ empty convention"          | { it.add("1") }
        ["1"]       | null          | []            | "append to unset value w/ empty convention"       | { it.append("1") }
        ["1"]       | _             | ["0"]         | "add to non-empty convention"                     | { it.add("1") }
        ["0", "1"]  | _             | ["0"]         | "append to non-empty convention"                  | { it.append("1") }
        null        | null          | ["0"]         | "add to unset value w/ non-empty convention"      | { it.add("1") }
        ["0", "1"]  | null          | ["0"]         | "append to unset value w/ non-empty convention"   | { it.append("1") }
        null        | notDefined()  | _             | "add to missing"                                  | { it.add("1") }
        ["1"]       | notDefined()  | _             | "append to missing"                               | { it.append("1") }
        null        | notDefined()  | ["0"]         | "add to missing w/ non-empty convention"          | { it.add("1") }
        ["1"]       | notDefined()  | ["0"]         | "append to missing w/ non-empty convention"       | { it.append("1") }
        null        | []            | _             | "add missing to empty value"                      | { it.add(notDefined()) }
        []          | []            | _             | "append missing to empty value"                   | { it.append(notDefined()) }
        null        | _             | _             | "add missing"                                     | { it.add(notDefined()) }
        []          | _             | _             | "append missing"                                  | { it.append(notDefined()) }
        ["1"]       | _             | _             | "add missing, then append"                        | { it.add(notDefined()) ; it.append("1") }
        ["1"]       | _             | _             | "append missing, then add"                        | { it.append(notDefined()) ; it.add("1") }
        ["1"]       | ["0"]         | _             | "add missing to non-empty value, then append"     | { it.add(notDefined()) ; it.append("1") }
        ["0", "1"]  | ["0"]         | _             | "append missing to non-empty value, then add"     | { it.append(notDefined()) ; it.add("1") }
        ["1"]       | _             | ["0"]         | "add missing to non-empty convention, then append"| { it.add(notDefined()) ; it.append("1") }
        ["0", "1"]  | _             | ["0"]         | "append missing to non-empty convention, then add"| { it.append(notDefined()) ; it.add("1") }
        ["1"]       | _             | _             | "add, then append missing"                        | { it.add("1") ; it.append(notDefined()) }
        null        | _             | _             | "append, then add missing"                        | { it.append("1") ; it.add(notDefined()) }
        ["0", "1"]  | ["0"]         | _             | "add to non-empty value, then append missing"     | { it.add("1") ; it.append(notDefined()) }
        null        | ["0"]         | _             | "append to non-empty value, then add missing"     | { it.append("1") ; it.add(notDefined()) }
        ["1"]       | _             | ["0"]         | "add to non-empty convention, then append missing"| { it.add("1") ; it.append(notDefined()) }
        null        | _             | ["0"]         | "append to non-empty conventio, then add missing" | { it.append("1") ; it.add(notDefined()) }
        ["1"]       | _             | _             | "add, then add missing, then append"              | { it.add("0") ; it.add(notDefined()) ; it.append("1") }
        ["0", "1"]  | _             | _             | "add, then append missing, then add"              | { it.add("0") ; it.append(notDefined()) ; it.add("1") }
    }

    def "execution time value is present if only undefined-safe operations are performed"() {
        given:
        property.set(notDefined())
        property.add(notDefined())
        property.append("2")
        property.addAll(['3'])
        property.addAll(['4'])
        property.append(notDefined())

        expect:
        assertValueIs(['2', '3', '4'])

        when:
        def execTimeValue = property.calculateExecutionTimeValue()

        then:
        assertCollectionIs(toImmutable(['2', '3', '4']), execTimeValue.toValue().get())
    }

    def "property restores undefined-safe items"() {
        given:
        property.add("1")
        property.appendAll(supplierWithChangingExecutionTimeValues(List, value, value))
        property.add("3")

        when:
        def execTimeValue = property.calculateExecutionTimeValue()
        def property2 = property()
        property2.fromState(execTimeValue)

        then:
        assertValueIs(result, property2)

        where:
        value | result
        ["2"] | ["1", "2", "3"]
        null  | ["1", "3"]
    }

    def "property remains undefined-safe after restored"() {
        given:
        property.append(notDefined())
        property.add("2")
        property.append(notDefined())
        property.append(notDefined())
        property.addAll(supplierWithChangingExecutionTimeValues(['3'], ['3a'], ['3b'], ['3c'], ['3d']))
        property.addAll(supplierWithValues(['4']))
        property.append(notDefined())

        when:
        def execTimeValue = property.calculateExecutionTimeValue()
        def property2 = property()
        property2.fromState(execTimeValue)

        then:
        assertValueIs(['2', '3a', '4'], property2)

        when:
        property2.add("5")
        property2.append("6")
        property2.append(notDefined())
        def execTimeValue2 = property2.calculateExecutionTimeValue()

        then:
        assertValueIs(['2', '3b', '4', '5', '6'], property2)

        when:
        def property3 = property()
        property3.fromState(execTimeValue2)

        then:
        assertValueIs(['2', '3d', '4', '5', '6'], property3)
    }

    def "can alternate append and add"() {
        when:
        property.append("1")
        property.add("2")
        property.append("3")

        then:
        assertValueIs toImmutable(["1", "2", "3"])
    }

    def "can alternate add and append"() {
        when:
        property.add("1")
        property.append("2")
        property.add("3")

        then:
        assertValueIs toImmutable(["1", "2", "3"])
    }

    def "has meaningful toString for #valueDescription"(Closure<AbstractCollectionProperty<String, C>> initializer, String stringValue) {
        given:
        def p = initializer.call()

        expect:
        p.toString() == stringValue

        where:
        valueDescription      | initializer                                    || stringValue
        "default"             | { propertyWithDefaultValue() }                 || "$collectionName(class ${String.name}, [])"
        "empty"               | { property().value([]) }                       || "$collectionName(class ${String.name}, [])"
        "unset"               | { propertyWithNoValue() }                      || "$collectionName(class ${String.name}, missing)"
        "s1"                  | { property().tap { add("s1") } }               || "$collectionName(class ${String.name}, [s1])"
        "[s1, s2]"            | { property().value(["s1, s2"]) }               || "$collectionName(class ${String.name}, [s1, s2])"
        "s1 + s2"             | { property().tap { add("s1"); add("s2") } }    || "$collectionName(class ${String.name}, [s1] + [s2])"
        "provider {s1}"       | { property().tap { add(Providers.of("s1")) } } || "$collectionName(class ${String.name}, item(fixed(class ${String.name}, s1)))"
        "provider {[s1, s2]}" | { property().value(Providers.of(["s1, s2"])) } || "$collectionName(class ${String.name}, fixed(class ${ArrayList.name}, [s1, s2]))"

        // The following case abuses Groovy lax type-checking to put an invalid value into the property.
        "[provider {s1}]"     | { property().value([Providers.of("s1")]) }     || "$collectionName(class ${String.name}, [fixed(class ${String.name}, s1)])"
    }

    def "can set explicit value to convention"() {
        given:
        property.convention(['1'])
        property.value(['4'])

        when:
        property.setToConvention()

        then:
        assertValueIs(['1'])
        property.explicit

        when:
        property.add('3')

        then:
        assertValueIs(['1', '3'])

        when:
        property.unset()

        then:
        assertValueIs(['1'])
        !property.explicit
    }

    def "can set explicit value to convention if not set yet"() {
        given:
        property.convention(['1'])
        property.value(['4'])

        when:
        property.setToConventionIfUnset()

        then:
        assertValueIs(['4'])

        when:
        property.unset()
        property.setToConventionIfUnset()

        then:
        assertValueIs(['1'])
        property.explicit
    }

    def "property is empty when setToConventionIfUnset if convention not set yet"() {
        when:
        property.setToConventionIfUnset()

        then:
        assertValueIs([])
        !property.explicit
    }
}
