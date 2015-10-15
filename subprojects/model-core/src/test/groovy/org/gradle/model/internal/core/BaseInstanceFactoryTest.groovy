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
    def factoryMock = Mock(BiFunction)

    def "can register public type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))

        expect:
        instanceFactory.getSupportedTypes() == ([ModelType.of(ThingSpec)] as Set)
    }

    def "can register implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()
    }

    def "can register internal view"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withInternalView(ModelType.of(ThingSpecInternal))

        expect:
        instanceFactory.getInternalViews(ModelType.of(ThingSpec)) == ([ModelType.of(ThingSpecInternal)] as Set)
    }

    def "can create instance"() {
        def thingMock = Mock(ThingSpec)
        def nodeMOck = Mock(MutableModelNode)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        def instance = instanceFactory.create(ModelType.of(ThingSpec), nodeMOck, "test")
        then:
        instance == thingMock
        1 * factoryMock.apply("test", nodeMOck) >> { thingMock }
        0 * _
    }

    def "fails when trying to create an unregistered type"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.create(ModelType.of(OtherThingSpec), Mock(MutableModelNode), "test")
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Cannot create a '$OtherThingSpec.name' because this type is not known to things. Known types are: $ThingSpec.name"
    }

    def "fails when an implementation is registered that doesn't extend the base type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(Object), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$Object.name' registered for '$ThingSpec.name' must extend '$BaseThingSpec.name'"
    }

    def "fails when an implementation is registered that doesn't have a default constructor"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(NoDefaultConstructorThingSpec), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$NoDefaultConstructorThingSpec.name' registered for '$ThingSpec.name' must have a public default constructor"
    }

    def "fails when registered implementation type is an abstract type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(AbstractThingSpec), factoryMock)
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '$AbstractThingSpec.name' registered for '$ThingSpec.name' must not be abstract"
    }

    def "fails when implementation type is registered twice"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)

        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule 2"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot register implementation for type '$ThingSpec.name' because an implementation for this type was already registered by test rule"
    }

    def "fails when asking for implementation info for a non-managed type"() {
        when:
        instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(ThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Type '$ThingSpec.name' is not managed"
    }

    def "fails validation if default implementation does not implement internal view"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("view rule"))
            .withInternalView(ModelType.of(NotImplementedInternalViewSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ThingSpec.name' is invalid because the implementation type '$DefaultThingSpec.name' does not extend internal view '$NotImplementedInternalViewSpec.name'" +
            ", implementation type was registered by impl rule, internal view was registered by view rule"
    }

    def "fails validation if managed type extends interface without default implementation"() {
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$ManagedThingSpec.name' is invalid because it doesn't extend an interface with a default implementation"
    }

    def "fails validation if unmanaged type extends interface with default implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(UnmanagedThingSpec), new SimpleModelRuleDescriptor("unmanaged thing"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$UnmanagedThingSpec.name' is invalid because no implementation was registered"
    }

    def "fails validation if unmanaged type extends two interface with a default implementation"() {
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(OtherThingSpec), new SimpleModelRuleDescriptor("other impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec), factoryMock)
        instanceFactory.register(ModelType.of(BothThingSpec), new SimpleModelRuleDescriptor("both rule"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '$BothThingSpec.name' is invalid because it has multiple default implementations registered, super-types that registered an implementation are: $ThingSpec.name, $OtherThingSpec.name"
    }
}
