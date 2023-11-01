/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Predicates
import com.google.common.collect.ImmutableMap
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.internal.state.ManagedFactory
import org.gradle.util.TestUtil
import org.gradle.util.internal.TextUtil
import org.spockframework.util.Assert
import spock.lang.Issue

class MapPropertySpec extends PropertySpec<Map<String, String>> {

    DefaultMapProperty<String, String> property() {
        new DefaultMapProperty<String, String>(host, String, String)
    }

    @Override
    DefaultMapProperty<String, String> propertyWithNoValue() {
        def p = property()
        p.set((Map) null)
        return p
    }

    @Override
    Class<Map<String, String>> type() {
        return Map
    }

    @Override
    Map<String, String> someValue() {
        return ['k1': 'v1', 'k2': 'v2']
    }

    @Override
    Map<String, String> someOtherValue() {
        return ['k1': 'v1']
    }

    @Override
    Map<String, String> someOtherValue2() {
        return ['k2': 'v2']
    }

    @Override
    Map<String, String> someOtherValue3() {
        return ['k3': 'v3']
    }

    @Override
    protected void setToNull(Object property) {
        property.set((Map) null)
    }

    @Override
    protected void nullConvention(Object property) {
        property.convention((Map) null)
    }

    @Override
    ManagedFactory managedFactory() {
        return new ManagedFactories.MapPropertyManagedFactory(TestUtil.propertyFactory())
    }

    def property = property()

    def "has empty map as value by default"() {
        expect:
        assertValueIs([:])
    }

    def "can change value to empty map"() {
        when:
        property.set([a: 'b'])
        property.empty()
        then:
        assertValueIs([:])
    }

    def "can set value using empty map"() {
        when:
        property.set([:])
        then:
        assertValueIs([:])
    }

    def "returns immutable copy of value"() {
        when:
        property.set(['k': 'v'])
        then:
        assertValueIs(['k': 'v'])
    }

    def "can set untyped from provider"() {
        given:
        def provider = Stub(ProviderInternal)
        provider.type >> null
        provider.calculateValue(_) >>> [['k1': 'v1'], ['k2': 'v2']].collect { ValueSupplier.Value.of(it) }

        when:
        property.setFromAnyValue(provider)

        then:
        property.get() == ['k1': 'v1']
        property.get() == ['k2': 'v2']
    }

    def "queries initial value for every call to get()"() {
        given:
        def initialValue = ['k1': 'v1']

        when:
        property.set(initialValue)
        then:
        assertValueIs(['k1': 'v1'])

        when:
        initialValue['addedKey'] = 'addedValue'
        then:
        assertValueIs(['k1': 'v1', 'addedKey': 'addedValue'])
    }

    def "queries underlying provider for every call to get()"() {
        given:
        def provider = Stub(ProviderInternal)
        provider.type >> Map
        provider.calculateValue(_) >>> [['k1': 'v1'], ['k2': 'v2']].collect { ValueSupplier.Value.of(it) }
        provider.calculatePresence(_) >> true
        and:
        property.set(provider)

        expect:
        assertValueIs(['k1': 'v1'])
        and:
        assertValueIs(['k2': 'v2'])
    }

    def "mapped provider is presented with immutable copy of value"() {
        given:
        property.set(['k1': 'v1'])
        def provider = property.map {
            assertImmutable(it)
            assert it == ['k1': 'v1']
            return ['k2': 'v2']
        }

        when:
        def actual = provider.get()
        then:
        actual == ['k2': 'v2']
    }

    def "adds a single entry using put"() {
        given:
        property.set(['k1': 'v1'])
        property.put('k2', 'v2')
        property.put('k3', 'v3')

        expect:
        assertValueIs(['k1': 'v1', 'k2': 'v2', 'k3': 'v3'])
    }

    def "put overrides entries added earlier"() {
        given:
        property.set(['k': 'v1'])
        property.put('k', 'v2')

        expect:
        assertValueIs(['k': 'v2'])
    }

    def "adds a single entry from value provider using put"() {
        given:
        property.set('k1': 'v1')
        property.put('k2', Providers.of('v2'))
        property.put('k3', Providers.of('v3'))

        expect:
        assertValueIs(['k1': 'v1', 'k2': 'v2', 'k3': 'v3'])
    }

    def "appends multiple entries from map #value using putAll"() {
        given:
        property.putAll(value)

        expect:
        assertValueIs(expectedValue)

        where:
        value                    | expectedValue
        [:]                      | [:]
        ['k1': 'v1']             | ['k1': 'v1']
        ['k1': 'v1', 'k2': 'v2'] | ['k1': 'v1', 'k2': 'v2']
    }

    def "appends multiple entries from provider #value using putAll"() {
        given:
        property.putAll(Providers.of(value))

        expect:
        assertValueIs(expectedValue)

        where:
        value                    | expectedValue
        [:]                      | [:]
        ['k1': 'v1']             | ['k1': 'v1']
        ['k1': 'v1', 'k2': 'v2'] | ['k1': 'v1', 'k2': 'v2']
    }

    def "empty map is used when entries added after convention set"() {
        given:
        property.convention([k1: 'v1'])
        property.put('k2', 'v2')

        expect:
        assertValueIs([k2: 'v2'])
    }

    def "queries entries of provider on every call to get()"() {
        given:
        def provider = Stub(ProviderInternal)
        _ * provider.type >> Map
        _ * provider.calculatePresence(_) >> true
        _ * provider.calculateValue(_) >>> [['k1': 'v1'], ['k2': 'v2']].collect { ValueSupplier.Value.of(it) }
        and:
        property.putAll(provider)

        expect:
        assertValueIs(['k1': 'v1'])
        and:
        assertValueIs(['k2': 'v2'])
    }

    def "queries entries of map on every call to get()"() {
        given:
        def value = ['k1': 'v1']
        property.putAll(value)

        expect:
        assertValueIs(['k1': 'v1'])

        when:
        value.put('addedKey', 'addedValue')
        then:
        assertValueIs(['k1': 'v1', 'addedKey': 'addedValue'])
    }

    def "providers only called once per query"() {
        given:
        def valueProvider = Mock(ProviderInternal)
        def putProvider = Mock(ProviderInternal)
        def putAllProvider = Mock(ProviderInternal)
        and:
        property.set(valueProvider)
        property.put('k2', putProvider)
        property.putAll(putAllProvider)

        when:
        property.present
        then:
        1 * valueProvider.calculatePresence(_) >> true
        1 * putProvider.calculatePresence(_) >> true
        1 * putAllProvider.calculatePresence(_) >> true
        0 * _

        when:
        property.get()
        then:
        1 * valueProvider.calculateValue(_) >> ValueSupplier.Value.of(['k1': 'v1'])
        1 * putProvider.calculateValue(_) >> ValueSupplier.Value.of('v2')
        1 * putAllProvider.calculateValue(_) >> ValueSupplier.Value.of(['k3': 'v3'])
        0 * _

        when:
        property.getOrNull()
        then:
        1 * valueProvider.calculateValue(_) >> ValueSupplier.Value.of(['k1': 'v1'])
        1 * putProvider.calculateValue(_) >> ValueSupplier.Value.of('v2')
        1 * putAllProvider.calculateValue(_) >> ValueSupplier.Value.of(['k3': 'v3'])
        0 * _
    }

    def "can add entries to property with default value"() {
        given:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        expect:
        assertValueIs([k1: 'v1', k2: 'v2', k3: 'v3', k4: 'v4'])
    }

    def "property has no value when set to provider with no value and other entries added"() {
        given:
        property.set(Providers.notDefined())
        and:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(['kk': 'vv']) == ['kk': 'vv']

        when:
        property.get()
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "property has no value when adding a value provider with no value"() {
        given:
        property.set(['k1': 'v1'])
        property.put('k2', 'v2')
        property.put('k3', Providers.notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(['kk': 'vv']) == ['kk': 'vv']

        when:
        property.get()
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "reports the source of value provider when value is missing and source is known"() {
        given:
        def provider = supplierWithNoValue(String, Describables.of("<source>"))
        property.set(['k1': 'v1'])
        property.put('k2', 'v2')
        property.put('k3', provider)

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of ${displayName} because it has no value available.
The value of this property is derived from: <source>""")
    }

    def "property has no value when adding a map provider with no value"() {
        given:
        property.set(['k1': 'v1'])
        property.put('k2', 'v2')
        property.putAll(Providers.notDefined())

        expect:
        !property.present
        property.getOrNull() == null
        property.getOrElse(['kk': 'vv']) == ['kk': 'vv']

        when:
        property.get()
        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of ${displayName} because it has no value available."
    }

    def "reports the source of map provider when value is missing and source is known"() {
        given:
        def provider = supplierWithNoValue(Describables.of("<source>"))
        property.set(['k1': 'v1'])
        property.put('k2', 'v2')
        property.putAll(provider)

        when:
        property.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of ${displayName} because it has no value available.
The value of this property is derived from: <source>""")
    }

    def "can set to null value to discard value"() {
        given:
        property.set(someValue())

        when:
        property.set((Map) null)

        then:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can set null value to remove any added entries"() {
        given:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        when:
        property.set((Map) null)

        then:
        !property.present
        property.getOrNull() == null
        property.getOrElse(someValue()) == someValue()
        property.getOrElse(null) == null
    }

    def "can set value to replace added entries"() {
        given:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        when:
        property.set(['kk': 'vv'])
        then:
        assertValueIs(['kk': 'vv'])
    }

    def "can make empty to replace added entries"() {
        given:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        when:
        property.empty()
        then:
        assertValueIs([:])
    }

    @Issue('gradle/gradle#11036')
    def "throws NullPointerException when provider returns map with null key to property"() {
        given:
        property.putAll(Providers.of(Collections.singletonMap(null, 'value')))

        when:
        property.get()

        then:
        def e = thrown NullPointerException
        e.message == 'Cannot get the value of a property of type java.util.Map with key type java.lang.String as the source contains a null key.'
    }

    @Issue('gradle/gradle#11036')
    def "throws NullPointerException when provider returns map with null value to property"() {
        given:
        property.putAll(Providers.of(['k': null]))

        when:
        property.get()

        then:
        def e = thrown NullPointerException
        e.message == 'Cannot get the value of a property of type java.util.Map with value type java.lang.String as the source contains a null value for key "k".'
    }

    def "throws NullPointerException when adding an entry with a null key to the property"() {
        when:
        property.put(null, (String) 'v')
        then:
        def ex = thrown NullPointerException
        ex.message == "Cannot add an entry with a null key to a property of type ${type().simpleName}."
    }

    def "throws NullPointerException when adding an entry with a null value to the property"() {
        when:
        property.put('k', (String) null)
        then:
        def ex = thrown NullPointerException
        ex.message == "Cannot add an entry with a null value to a property of type ${type().simpleName}."
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
        property.putAll([a: '1', b: '2'])
        property.put('k', supplierWithNoValue(String, displayName('thing')))
        property.put('c', '3')
        property.putAll(supplierWithValues([d: '4']))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has no producer and missing execution time value when selement provider with no value added"() {
        given:
        property.putAll([a: '1', b: '2'])
        property.put('k', supplierWithValues('3'))
        property.put('c', '3')
        property.putAll(supplierWithNoValue())

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isMissing()
    }

    def "has no producer and fixed execution time value when element added"() {
        given:
        property.put('a', '1')
        property.put('b', '2')

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == [a: '1', b: '2']
    }

    def "has no producer and fixed execution time value when elements added"() {
        given:
        property.putAll(a: '1')
        property.putAll(b: '2')

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == [a: '1', b: '2']
    }

    def "has no producer and fixed execution time value when element provider added"() {
        given:
        property.put('a', supplierWithValues('1'))
        property.put('b', supplierWithValues('2'))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == [a: '1', b: '2']
    }

    def "has no producer and fixed execution time value when elements provider added"() {
        given:
        property.putAll(supplierWithValues(a: '1'))
        property.putAll(supplierWithValues(b: '2'))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.hasFixedValue()
        !value.hasChangingContent()
        value.getFixedValue() == [a: '1', b: '2']
    }

    def "has no producer and changing execution time value when elements provider with changing value added"() {
        given:
        property.putAll(supplierWithChangingExecutionTimeValues([a: '1', b: '2'], [a: '1b']))
        property.putAll(supplierWithValues([c: '3']))

        expect:
        assertHasNoProducer(property)
        def value = property.calculateExecutionTimeValue()
        value.isChangingValue()
        value.getChangingValue().get() == [a: '1', b: '2', c: '3']
        value.getChangingValue().get() == [a: '1b', c: '3']
    }

    def "has union of producer task from providers unless producer task attached"() {
        given:
        def task1 = Stub(Task)
        def task2 = Stub(Task)
        def task3 = Stub(Task)
        def producer = Stub(Task)
        property.set(supplierWithProducer(task1))
        property.putAll(supplierWithProducer(task2))
        property.put('a', supplierWithProducer(task3, '1'))

        expect:
        assertHasProducer(property, task1, task2, task3)

        property.attachProducer(owner(producer))
        assertHasProducer(property, producer)
    }

    def "cannot set to empty map after value finalized"() {
        given:
        property.set(someValue())
        property.finalizeValue()

        when:
        property.empty()

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot set to empty map after value finalized implicitly"() {
        given:
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.empty()


        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot set to empty map after changes disallowed"() {
        given:
        property.set(someValue())
        property.disallowChanges()
        when:
        property.empty()
        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add entry after value finalized"() {
        given:
        property.set(someValue())
        property.finalizeValue()

        when:
        property.put('k1', 'v1')

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.put('k2', brokenValueSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "cannot add entry after value finalized implicitly"() {
        given:
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.put('k1', 'v1')

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.put('k2', brokenValueSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add entry after changes disallowed"() {
        given:
        property.set(someValue())
        property.disallowChanges()

        when:
        property.put('k1', 'v1')

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.put('k2', brokenValueSupplier())

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add entries after value finalized"() {
        given:
        property.set(someValue())
        property.finalizeValue()

        when:
        property.putAll(['k3': 'v3', 'k4': 'v4'])

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property is final and cannot be changed any further.'

        when:
        property.putAll brokenSupplier()

        then:
        def e2 = thrown IllegalStateException
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores add entries after value finalized implicitly"() {
        given:
        property.set(someValue())
        property.implicitFinalizeValue()

        when:
        property.putAll(['k3': 'v3'])

        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.putAll brokenSupplier()

        then:
        def e2 = thrown IllegalStateException
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "cannot add entries after changes disallowed"() {
        given:
        property.set(someValue())
        property.disallowChanges()

        when:
        property.putAll(['k3': 'v3', 'k4': 'v4'])
        then:
        def e = thrown IllegalStateException
        e.message == 'The value for this property cannot be changed any further.'

        when:
        property.putAll brokenSupplier()
        then:
        def e2 = thrown IllegalStateException
        e2.message == 'The value for this property cannot be changed any further.'
    }

    def "entry provider has no value when property has no value"() {
        given:
        def entryProvider = property.getting('key')

        expect:
        !entryProvider.present
        entryProvider.getOrNull() == null

        when:
        entryProvider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this provider because it has no value available."
    }

    def "entry provider has no value when key is not in map"() {
        given:
        def entryProvider = property.getting('key')
        property.set(['k1': 'v1', 'k2': 'v2'])

        expect:
        !entryProvider.present
        entryProvider.getOrNull() == null

        when:
        entryProvider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this provider because it has no value available."
    }

    def "entry provider tracks value of property"() {
        given:
        def entryProvider = property.getting('key')

        when:
        property.set(['key': 'v1'])
        then:
        entryProvider.present
        entryProvider.get() == 'v1'
        entryProvider.getOrNull() == 'v1'

        when:
        property.set(Providers.of(['key': 'v2']))
        then:
        entryProvider.present
        entryProvider.get() == 'v2'
        entryProvider.getOrNull() == 'v2'

        when:
        property.set(Providers.of([:]))
        then:
        !entryProvider.present
        entryProvider.getOrNull() == null
    }

    def "entry provider tracks value of last added entry"() {
        given:
        def entryProvider = property.getting('key')

        when:
        property.put('key', 'v1')
        then:
        entryProvider.present
        entryProvider.get() == 'v1'
        entryProvider.getOrNull() == 'v1'

        when:
        property.put('key', Providers.of('v2'))
        then:
        entryProvider.present
        entryProvider.get() == 'v2'
        entryProvider.getOrNull() == 'v2'

        when:
        property.putAll(['key': 'v3'])
        then:
        entryProvider.present
        entryProvider.get() == 'v3'
        entryProvider.getOrNull() == 'v3'

        when:
        property.putAll(Providers.of(['key': 'v4']))
        then:
        entryProvider.present
        entryProvider.get() == 'v4'
        entryProvider.getOrNull() == 'v4'
    }

    def "keySet provider has no value when property has no value"() {
        given:
        property.set((Map) null)
        def keySetProvider = property.keySet()

        expect:
        !keySetProvider.present
        keySetProvider.getOrNull() == null

        when:
        keySetProvider.get()

        then:
        def e = thrown(MissingValueException)
        e.message == "Cannot query the value of this provider because it has no value available."
    }

    @Issue('gradle/gradle#11036')
    def "getting key set from provider throws NullPointerException if property has null key"() {
        given:
        property.putAll(Providers.of(Collections.singletonMap(null, 'value')))
        def keySetProvider = property.keySet()

        when:
        keySetProvider.get()

        then:
        def e = thrown(NullPointerException)
        e.message == 'Cannot get the value of a property of type java.util.Set with element type java.lang.String as the source value contains a null element.'
    }

    def "keySet provider tracks value of property"() {
        when:
        def keySetProvider = property.keySet()

        then:
        keySetProvider.present
        keySetProvider.get() == [] as Set
        keySetProvider.getOrNull() == [] as Set

        when:
        property.set(['k1': 'v1', 'k2': 'v2'])

        then:
        keySetProvider.present
        keySetProvider.get() == ['k1', 'k2'] as Set
        keySetProvider.getOrNull() == ['k1', 'k2'] as Set
    }

    def "keySet provider includes keys of added entries"() {
        given:
        def keySetProvider = property.keySet()

        when:
        property.put('k1', 'v1')
        then:
        keySetProvider.present
        keySetProvider.get() == ['k1'] as Set
        keySetProvider.getOrNull() == ['k1'] as Set

        when:
        property.put('k2', Providers.of('v2'))
        then:
        keySetProvider.present
        keySetProvider.get() == ['k1', 'k2'] as Set
        keySetProvider.getOrNull() == ['k1', 'k2'] as Set

        when:
        property.putAll(['k3': 'v3'])
        then:
        keySetProvider.present
        keySetProvider.get() == ['k1', 'k2', 'k3'] as Set
        keySetProvider.getOrNull() == ['k1', 'k2', 'k3'] as Set

        when:
        property.putAll(Providers.of(['k4': 'v4']))
        then:
        keySetProvider.present
        keySetProvider.get() == ['k1', 'k2', 'k3', 'k4'] as Set
        keySetProvider.getOrNull() == ['k1', 'k2', 'k3', 'k4'] as Set
    }

    def "implicitly finalizes value on read of keys"() {
        def provider = Mock(ProviderInternal)

        given:
        property.put('k1', provider)
        property.implicitFinalizeValue()

        when:
        def p = property.keySet()

        then:
        0 * _

        when:
        def result = p.get()

        then:
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of("value")
        0 * _

        and:
        result == (['k1'] as Set)

        when:
        def result2 = property.get()

        then:
        0 * _

        and:
        result2 == [k1: "value"]
    }

    def "implicitly finalizes value on read of entry"() {
        def provider = Mock(ProviderInternal)

        given:
        property.put('k1', provider)
        property.implicitFinalizeValue()

        when:
        def p = property.getting('k1')

        then:
        0 * _

        when:
        def result = p.get()

        then:
        1 * provider.calculateValue(_) >> ValueSupplier.Value.of("value")
        0 * _

        and:
        result == "value"

        when:
        def result2 = property.get()

        then:
        0 * _

        and:
        result2 == [k1: "value"]
    }

    def "finalizes upstream properties when value read using #method and disallow unsafe reads"() {
        def a = property()
        def b = property()
        def c = valueProperty()
        def property = property()
        property.disallowUnsafeRead()

        property.putAll(a)

        a.putAll(b)
        a.attachOwner(owner(), displayName("<a>"))

        b.attachOwner(owner(), displayName("<b>"))

        property.put("c", c)
        c.set("c")
        c.attachOwner(owner(), displayName("<c>"))

        given:
        property."$method"()

        when:
        a.set([a: 'a'])

        then:
        def e1 = thrown(IllegalStateException)
        e1.message == 'The value for <a> is final and cannot be changed any further.'

        when:
        b.set([b: 'b'])

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

    Property<String> valueProperty() {
        return new DefaultProperty<String>(host, String)
    }

    def "runs side effect when calling '#getter' on property to which providers were added via 'put'"() {
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)
        def expectedUnpackedValue = ["some key": "some value", "other key": "other value"]

        when:
        property.put("some key", Providers.of("some value").withSideEffect(sideEffect1))
        property.put("other key", Providers.of("other value").withSideEffect(sideEffect2))

        def value = property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        def executionTimeValue = property.calculateExecutionTimeValue()
        then:
        0 * _ // no side effects until values are unpacked

        when:
        def unpackedValue = value.get()
        then:
        unpackedValue == expectedUnpackedValue
        1 * sideEffect1.execute("some value")
        then: // ensure ordering
        1 * sideEffect2.execute("other value")
        0 * _

        when:
        unpackedValue = executionTimeValue.toValue().get()
        then:
        unpackedValue == expectedUnpackedValue
        1 * sideEffect1.execute("some value")
        then: // ensure ordering
        1 * sideEffect2.execute("other value")
        0 * _

        when:
        unpackedValue = getter(property, getter, ["yet another key": "yet another value"])
        then:
        unpackedValue == expectedUnpackedValue
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

    def "runs side effect when calling '#getter' on property to which providers were added via 'putAll'"() {
        def sideEffect = Mock(ValueSupplier.SideEffect)

        when:
        property.putAll(Providers.of(someValue()).withSideEffect(sideEffect))

        def value = property.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead)
        def executionTimeValue = property.calculateExecutionTimeValue()
        then:
        0 * _ // no side effects until values are unpacked

        when:
        def unpackedValue = value.get()
        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        when:
        unpackedValue = executionTimeValue.toValue().get()
        then:
        unpackedValue == someValue()
        1 * sideEffect.execute(someValue())
        0 * _

        when:
        unpackedValue = getter(property, getter, ["yet another key": "yet another value"])
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

    def "runs side effect when getting #description"() {
        def valueSideEffect = Mock(ValueSupplier.SideEffect)

        when:
        property.put("some key", Providers.of("some value").withSideEffect(valueSideEffect))
        def valueProvider = property.getting(key)
        then:
        0 * _ // no side effects until values are unpacked

        when:
        valueProvider.getOrNull()
        then:
        expectSideEffect * valueSideEffect.execute("some value")
        0 * _

        where:
        description        | key        | expectSideEffect
        "existing key"     | "some key" | 1
        "non-existing key" | "oops key" | 0
    }

    def "runs side effect when calling '#getter' on property's 'keySet'"() {
        def sideEffect1 = Mock(ValueSupplier.SideEffect)
        def sideEffect2 = Mock(ValueSupplier.SideEffect)

        when:
        property.put("some key", Providers.of("some value").withSideEffect(sideEffect1))
        property.putAll(Providers.of(["other key": "other value"]).withSideEffect(sideEffect2))
        def keySetProvider = property.keySet()
        then:
        0 * _

        when:
        def keySetValue = keySetProvider.get()
        then:
        keySetValue == ["some key", "other key"].toSet()
        // provider of the value in the Map entry does not need to be unpacked
        0 * sideEffect1.execute("some value")
        // provider of the whole map on the other hand must be unpacked and propagates the side effect
        1 * sideEffect2.execute(["other key": "other value"])
        0 * _

        where:
        getter      | _
        "get"       | _
        "getOrNull" | _
        "getOrElse" | _
    }


    def "pruning a property ensures existing undefined providers are gone"() {
        given:
        property.put('m1', 'foo')
        property.set(Providers.notDefined())
        property.prune()
        and:
        property.put('k1', 'v1')
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        expect:
        assertValueIs(['k1': 'v1', 'k2': 'v2', 'k3': 'v3', 'k4': 'v4'])
    }

    def "pruning a property ensures future undefined providers are ignored"() {
        given:
        property.set(Providers.notDefined())
        property.prune()
        and:
        property.put('k1', 'v1')
        property.put('m1', Providers.notDefined())
        property.put('k2', Providers.of('v2'))
        property.putAll(['k3': 'v3'])
        property.putAll(Providers.of(['k4': 'v4']))

        expect:
        assertValueIs(['k1': 'v1', 'k2': 'v2', 'k3': 'v3', 'k4': 'v4'])
    }

    def "may make further changes for a map originally frozen due to containing notDefined after pruning"() {
        given:
        property.put('k1', 'v1')
        property.put('k2', 'v2')
        property.put('k2', "v2b")
        property.put('k3', "v3")
        property.put('k2', Providers.notDefined())
        property.put('k2', "v2c")
        property.put('k0', "v0")
        property.prune()
        property.put('k1', "v1b")
        property.put('k4', 'v4')

        expect:
        assertValueIs(['k1': 'v1b', 'k2': 'v2b', 'k3': 'v3', 'k4': 'v4'])
    }

    def "may exclude entries based on key"() {
        given:
        property.put('k0', "1")
        property.put('k1', '2')
        property.put('k2', '3')
        property.put('k3', '4')
        property.put('k4', '5')
        property.exclude({ it > "k2" } )

        expect:
        assertValueIs(['k0': '1', 'k1': '2', 'k2': '3'])
    }

    def "may exclude entries based on value"() {
        given:
        property.put('k0', "1")
        property.put('k1', '2')
        property.put('k2', '3')
        property.put('k3', '4')
        property.put('k4', '5')
        property.exclude(Predicates.alwaysFalse(), { it.toInteger() % 2 as Boolean} )

        expect:
        assertValueIs(['k1': '2', 'k3': '4'])
    }

    private ProviderInternal<String> brokenValueSupplier() {
        return brokenSupplier(String)
    }

    private void assertValueIs(Map<String, String> expected) {
        assert property.present
        def actual = property.get()
        assertImmutable(actual)
        actual.each {
            assert it.key instanceof String
            assert it.value instanceof String
        }
        assert actual == ImmutableMap.copyOf(expected)
    }

    private static void assertImmutable(Map<String, String> map) {
        try {
            map.put("immutableTest", "value")
            Assert.fail('Map is not immutable')
        } catch (UnsupportedOperationException ignored) {
            // expected
        }
    }
}
