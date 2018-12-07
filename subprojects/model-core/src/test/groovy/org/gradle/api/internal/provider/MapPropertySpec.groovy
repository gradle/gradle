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

import com.google.common.collect.ImmutableMap
import org.spockframework.util.Assert

class MapPropertySpec extends PropertySpec<Map<String, String>> {

    DefaultMapProperty<String, String> property() {
        new DefaultMapProperty<String, String>(String, String)
    }

    @Override
    DefaultMapProperty<String, String> propertyWithDefaultValue() {
        return property()
    }

    @Override
    DefaultMapProperty<String, String> propertyWithNoValue() {
        def p = property()
        p.set((Map) null)
        return p
    }

    @Override
    DefaultMapProperty<String, String> providerWithValue(Map<String, String> value) {
        def p = property()
        p.set(value)
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
    protected void setToNull(Object property) {
        property.set((Map) null)
    }

    def property = property()

    def "has empty map as value by default"() {
        expect:
        assertValueIs([:])
    }

    def "can change value to empty map"() {
        when:
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
        provider.get() >>> [['k1': 'v1'], ['k2': 'v2']]

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
        provider.get() >>> [['k1': 'v1'], ['k2': 'v2']]
        provider.present >> true
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
        assertValueIs([k2:  'v2'])
    }

    def "queries entries of provider on every call to get()"() {
        given:
        def provider = Stub(ProviderInternal)
        _ * provider.type >> Map
        _ * provider.present >> true
        _ * provider.get() >>> [['k1': 'v1'], ['k2': 'v2']]
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
        1 * valueProvider.present >> true
        1 * putProvider.present >> true
        1 * putAllProvider.present >> true
        0 * _

        when:
        property.get()
        then:
        1 * valueProvider.get() >> ['k1': 'v1']
        1 * putProvider.get() >> 'v2'
        1 * putAllProvider.get() >> ['k3': 'v3']
        0 * _

        when:
        property.getOrNull()
        then:
        1 * valueProvider.getOrNull() >> ['k1': 'v1']
        1 * putProvider.getOrNull() >> 'v2'
        1 * putAllProvider.getOrNull() >> ['k3': 'v3']
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
        e.message == Providers.NULL_VALUE
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
        e.message == Providers.NULL_VALUE
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
        e.message == Providers.NULL_VALUE
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

    def "throws NullPointerException when provider returns map with null key to property"() {
        given:
        property.putAll(Providers.of(Collections.singletonMap(null, 'value')))

        when:
        property.get()

        then:
        thrown NullPointerException
    }

    def "throws NullPointerException when provider returns map with null value to property"() {
        given:
        property.putAll(Providers.of(['k': null]))

        when:
        property.get()

        then:
        thrown NullPointerException
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

    def "ignores set to empty map after value finalized leniently"() {
        given:
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.empty()

        then:
        assertValueIs someValue()
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
        property.put('k2', Stub(ProviderInternal))

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores add entry after value finalized leniently"() {
        given:
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.put('k1', 'v1')
        property.put('k2', Stub(ProviderInternal))

        then:
        assertValueIs someValue()
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
        property.putAll Stub(ProviderInternal)
        then:
        def e2 = thrown IllegalStateException
        e2.message == 'The value for this property is final and cannot be changed any further.'
    }

    def "ignores add entries after value finalized leniently"() {
        given:
        property.set(someValue())
        property.finalizeValueOnReadAndWarnAboutChanges()
        property.get()

        when:
        property.putAll(['k3': 'v3'])
        property.putAll(Stub(ProviderInternal))

        then:
        assertValueIs someValue()
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
        thrown IllegalStateException
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
        thrown IllegalStateException
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
        thrown IllegalStateException
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
