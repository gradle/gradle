/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.core

import org.gradle.internal.util.BiFunction
import org.gradle.model.Managed
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

class BaseInstanceFactoryTest extends Specification {
    static interface ThingSpec {}
    static interface ThingSpecInternal extends ThingSpec {}
    static abstract class BaseThingSpec implements ThingSpecInternal {}
    static class DefaultThingSpec extends BaseThingSpec {}
    static abstract class AbstractThingSpec implements ThingSpec {}

    static class NoDefaultConstructorThingSpec extends BaseThingSpec {
        @SuppressWarnings("GroovyUnusedDeclaration")
        NoDefaultConstructorThingSpec(String name) {
        }
    }
    static interface NotImplementedInternalViewSpec extends ThingSpec {}

    static interface OtherThingSpec extends ThingSpec {}
    static @Managed interface ManagedThingSpec extends ThingSpec {}
    static interface UnmanagedThingSpec extends ThingSpec {}
    static @Managed interface BothThingSpec extends ThingSpec, OtherThingSpec {}

    def instanceFactory = new BaseInstanceFactory<ThingSpec>("things", ThingSpec, BaseThingSpec)
    def node = Mock(MutableModelNode)

    def "can register factory"() {
        def factoryMock = Mock(BiFunction)
        instanceFactory.registerFactory(ModelType.of(ThingSpec), null, factoryMock)

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()
    }

    def "can register public type"() {
        instanceFactory.registerPublicType(ModelType.of(ThingSpec))

        expect:
        instanceFactory.getSupportedTypes() == ([ModelType.of(ThingSpec)] as Set)
    }

    def "can register internal view"() {
        instanceFactory.registerInternalView(ModelType.of(ThingSpec), null, ModelType.of(ThingSpecInternal))

        expect:
        instanceFactory.getInternalViews(ModelType.of(ThingSpec)) == ([ModelType.of(ThingSpecInternal)] as Set)
    }

    def "can create instance"() {
        def factoryMock = Mock(BiFunction)
        def thingMock = Mock(ThingSpec)
        instanceFactory.registerFactory(ModelType.of(ThingSpec), null, factoryMock)

        when:
        def instance = instanceFactory.create(ModelType.of(ThingSpec), node, "test")
        then:
        instance == thingMock
        1 * factoryMock.apply("test", node) >> { thingMock }
        0 * _
    }

    def "fails when trying to create an unregistered type"() {
        def factoryMock = Mock(BiFunction)
        instanceFactory.registerFactory(ModelType.of(ThingSpec), null, factoryMock)

        when:
        instanceFactory.create(ModelType.of(OtherThingSpec), node, "test")
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Cannot create a BaseInstanceFactoryTest.OtherThingSpec because this type is not known to things. Known types are: ThingSpec"
    }

    def "fails when factory is registered twice"() {
        def factoryMock = Mock(BiFunction)
        instanceFactory.registerFactory(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule"), factoryMock)

        when:
        instanceFactory.registerFactory(ModelType.of(ThingSpec), null, factoryMock)
        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot register a factory for type '$ThingSpec.name' because a factory for this type was already registered by test rule"
    }

    def "fails when an implementation type is registered that doesn't extend the base type"() {
        when:
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), null, ModelType.of(Object))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$Object.name' registered for '$ThingSpec.name' must extend '$BaseThingSpec.name'"
    }

    def "fails when an implementation type is registered that doesn't have a default constructor"() {
        when:
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), null, ModelType.of(NoDefaultConstructorThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$NoDefaultConstructorThingSpec.name' registered for '$ThingSpec.name' must have a public default constructor"
    }

    def "fails when registered implementation type is an abstract type"() {
        when:
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), null, ModelType.of(AbstractThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$AbstractThingSpec.name' registered for '$ThingSpec.name' must not be abstract"
    }

    def "fails when implementation type is registered twice"() {
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule"), ModelType.of(DefaultThingSpec))

        when:
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), null, ModelType.of(DefaultThingSpec))
        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot register an implementation type for type '$ThingSpec.name' because an implementation type for this type was already registered by test rule"
    }

    def "fails validation if default implementation does not implement internal view"() {
        instanceFactory.registerPublicType(ModelType.of(ThingSpec))
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"), ModelType.of(DefaultThingSpec))
        instanceFactory.registerInternalView(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("view rule"), ModelType.of(NotImplementedInternalViewSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ThingSpec.name' is invalid because the implementation type '$DefaultThingSpec.name' does not extend internal view '$NotImplementedInternalViewSpec.name'" +
            ", implementation type was registered by impl rule, internal view was registered by view rule"
    }

    def "fails validation if managed type extends interface without default implementation"() {
        instanceFactory.registerPublicType(ModelType.of(ManagedThingSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ManagedThingSpec.name' is invalid because it doesn't extend an interface with a default implementation"
    }

    def "fails validation if unmanaged type extends interface with default implementation"() {
        instanceFactory.registerPublicType(ModelType.of(ThingSpec))
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"), ModelType.of(DefaultThingSpec))
        instanceFactory.registerPublicType(ModelType.of(UnmanagedThingSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$UnmanagedThingSpec.name' is invalid because only managed types can extend unmanaged type '$ThingSpec.name'"
    }

    def "fails validation if unmanaged type extends two interface with a default implementation"() {
        instanceFactory.registerPublicType(ModelType.of(ThingSpec))
        instanceFactory.registerImplementation(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"), ModelType.of(DefaultThingSpec))
        instanceFactory.registerPublicType(ModelType.of(OtherThingSpec))
        instanceFactory.registerImplementation(ModelType.of(OtherThingSpec), new SimpleModelRuleDescriptor("other impl rule"), ModelType.of(DefaultThingSpec))
        instanceFactory.registerPublicType(ModelType.of(BothThingSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$BothThingSpec.name' is invalid because it has multiple default implementations registered, super-types that registered an implementation are: $ThingSpec.name, $OtherThingSpec.name"
    }
}
