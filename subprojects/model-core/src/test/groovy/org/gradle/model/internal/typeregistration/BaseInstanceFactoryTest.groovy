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

package org.gradle.model.internal.typeregistration

import org.gradle.model.Managed
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.core.MutableModelNode
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.type.ModelType
import spock.lang.Specification

import static org.gradle.model.ModelTypeTesting.fullyQualifiedNameOf
import static org.gradle.model.internal.typeregistration.BaseInstanceFactory.ImplementationFactory

class BaseInstanceFactoryTest extends Specification {
    static interface ThingSpec {}
    static interface ThingSpecInternal extends ThingSpec {}
    static abstract class BaseThingSpec implements ThingSpecInternal {}
    static class DefaultThingSpec extends BaseThingSpec {}
    static class DefaultOtherThingSpec extends DefaultThingSpec implements OtherThingSpec {}
    static abstract class AbstractThingSpec implements ThingSpec {}

    static class NoDefaultConstructorThingSpec extends BaseThingSpec {
        @SuppressWarnings("GroovyUnusedDeclaration")
        NoDefaultConstructorThingSpec(String name) {
        }
    }
    static interface NotImplementedInternalViewSpec extends ThingSpec {}

    static interface OtherThingSpec extends ThingSpec {}
    static @Managed interface ManagedThingSpec extends ThingSpec {}
    static @Managed interface ChildManagedThingSpec extends ManagedThingSpec {}
    static @Managed interface ManagedThingSpecInternal {}
    static @Managed interface ManagedThingSpecInternalExtendingThingSpecInternal extends ThingSpecInternal {}
    static @Managed interface ManagedThingSpecInternalExtendingOtherThingSpec extends OtherThingSpec {}
    static @Managed interface MultiplePathsToRoot extends ThingSpec, ManagedThingSpecInternalExtendingOtherThingSpec {}
    static interface UnmanagedThingSpec extends ThingSpec {}
    static @Managed interface BothThingSpec extends ThingSpec, OtherThingSpec {}

    def instanceFactory = new BaseInstanceFactory<ThingSpec>(ThingSpec)
    def node = Mock(MutableModelNode)
    def factoryMock = Mock(ImplementationFactory)

    def "can register public type"() {
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("thing"))

        expect:
        instanceFactory.getSupportedTypes() == ([ModelType.of(ManagedThingSpec)] as Set)
    }

    def "can register implementation"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))

        when:
        instanceFactory.validateRegistrations()

        then:
        noExceptionThrown()
    }

    def "can register unmanaged internal view for unmanaged type"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
            .withInternalView(ModelType.of(ThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()

        then:
        noExceptionThrown()

        expect:
        instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).internalViews == ([ModelType.of(ThingSpecInternal)] as Set)
    }

    def "can register managed internal view for unmanaged type"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
            .withInternalView(ModelType.of(ManagedThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()

        expect:
        instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).internalViews == ([ModelType.of(ManagedThingSpecInternal)] as Set)
    }

    def "internal views registered for super-type are returned"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("base"))
            .withImplementation(ModelType.of(DefaultThingSpec))
            .withInternalView(ModelType.of(ThingSpecInternal))
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed"))
            .withInternalView(ModelType.of(ManagedThingSpecInternal))
        instanceFactory.register(ModelType.of(ChildManagedThingSpec), new SimpleModelRuleDescriptor("child"))

        expect:
        instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).internalViews == ([
            ModelType.of(ThingSpecInternal),
        ] as Set)
        instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(ManagedThingSpec)).internalViews == ([
            ModelType.of(ThingSpecInternal),
            ModelType.of(ManagedThingSpecInternal)
        ] as Set)
        instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(ChildManagedThingSpec)).internalViews == ([
            ModelType.of(ThingSpecInternal),
            ModelType.of(ManagedThingSpecInternal)
        ] as Set)
    }

    def "uses provided factory to create implementation for a public type"() {
        def thingMock = Mock(ThingSpec)
        def nodeMock = Mock(MutableModelNode)
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))

        when:
        def instance = instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).create(nodeMock)

        then:
        instance == thingMock
        _ * nodeMock.path >> ModelPath.path("node.test")
        1 * factoryMock.create(ModelType.of(ThingSpec), ModelType.of(DefaultThingSpec), "test", nodeMock) >> { thingMock }
        0 * _
    }

    def "cannot create delegate when no factory is available"() {
        instanceFactory.registerFactory(DefaultOtherThingSpec, factoryMock)

        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))

        then:
        IllegalArgumentException e = thrown()
        e.message == "No factory registered to create an instance of implementation class '${fullyQualifiedNameOf(DefaultThingSpec)}'."
    }

    def "factory for closest superclass to create implementation for a public type"() {
        def thingMock = Mock(ThingSpec)
        def nodeMock = Mock(MutableModelNode)
        instanceFactory.registerFactory(BaseThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))

        when:
        def instance = instanceFactory.getImplementationInfo(ModelType.of(ThingSpec)).create(nodeMock)

        then:
        instance == thingMock
        _ * nodeMock.path >> ModelPath.path("node.test")
        1 * factoryMock.create(ModelType.of(ThingSpec), ModelType.of(DefaultThingSpec), "test", nodeMock) >> { thingMock }
        0 * _
    }

    def "uses most specific implementation type for managed public type"() {
        def thingMock = Mock(ThingSpec)
        def nodeMock = Mock(MutableModelNode)
        instanceFactory.registerFactory(BaseThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        instanceFactory.register(ModelType.of(OtherThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultOtherThingSpec))
        instanceFactory.register(ModelType.of(MultiplePathsToRoot), new SimpleModelRuleDescriptor("thing"))

        when:
        def instance = instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(MultiplePathsToRoot)).create(nodeMock)

        then:
        instance == thingMock
        _ * nodeMock.path >> ModelPath.path("node.test")
        1 * factoryMock.create(ModelType.of(MultiplePathsToRoot), ModelType.of(DefaultOtherThingSpec), "test", nodeMock) >> { thingMock }
        0 * _
    }

    def "non-constructible types are not reported as supported types"() {
        instanceFactory.registerFactory(BaseThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        instanceFactory.register(ModelType.of(OtherThingSpec), new SimpleModelRuleDescriptor("other"))
            .withImplementation(ModelType.of(DefaultOtherThingSpec))

        expect:
        instanceFactory.supportedTypes == [ThingSpec, OtherThingSpec].collect { ModelType.of(it) } as Set
    }

    def "returns null implementation for an unknown type"() {
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("thing"))

        expect:
        instanceFactory.getImplementationInfo(ModelType.of(ChildManagedThingSpec)) == null
    }

    def "fails when an implementation is registered that doesn't extend the base type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(Object))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "No factory registered to create an instance of implementation class '$Object.name'."
    }

    def "fails when registered implementation type is an abstract type"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(AbstractThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Implementation type '${fullyQualifiedNameOf(AbstractThingSpec)}' registered for '${fullyQualifiedNameOf(ThingSpec)}' must not be abstract"
    }

    def "fails when implementation type is registered twice"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule"))
            .withImplementation(ModelType.of(DefaultThingSpec))

        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("test rule 2"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        then:
        def ex = thrown IllegalStateException
        ex.message == "Cannot register implementation for type '${fullyQualifiedNameOf(ThingSpec)}' because an implementation for this type was already registered by test rule"
    }

    def "fails when asking for implementation info for a non-managed type"() {
        when:
        instanceFactory.getManagedSubtypeImplementationInfo(ModelType.of(ThingSpec))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Type '${fullyQualifiedNameOf(ThingSpec)}' is not managed"
    }

    def "fails validation if default implementation does not implement internal view"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("impl rule"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("view rule"))
            .withInternalView(ModelType.of(NotImplementedInternalViewSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '${fullyQualifiedNameOf(ThingSpec)}' is invalid because the implementation type '${fullyQualifiedNameOf(DefaultThingSpec)}' does not implement internal view '${fullyQualifiedNameOf(NotImplementedInternalViewSpec)}'" +
            ", implementation type was registered by impl rule, internal view was registered by view rule"
    }

    def "fails validation if managed type extends interface without default implementation"() {
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '${fullyQualifiedNameOf(ManagedThingSpec)}' is invalid because it doesn't extend an interface with a default implementation"
    }

    def "fails when registering non-interface internal view"() {
        when:
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withInternalView(ModelType.of(Object))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Internal view '$Object.name' registered for '${fullyQualifiedNameOf(ThingSpec)}' must be an interface"
    }

    def "fails when registering unmanaged internal view for managed type"() {
        when:
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withInternalView(ModelType.of(ThingSpecInternal))
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Internal view '${fullyQualifiedNameOf(ThingSpecInternal)}' registered for managed type '${fullyQualifiedNameOf(ManagedThingSpec)}' must be managed"
    }

    def "can register managed internal view for managed type that extends interface that is implemented by delegate type"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))
            .withInternalView(ModelType.of(ManagedThingSpecInternalExtendingThingSpecInternal))

        when:
        instanceFactory.validateRegistrations()
        then:
        noExceptionThrown()
    }

    def "fails when registering managed internal view for managed type that extends interface that is not implemented by delegate type"() {
        instanceFactory.registerFactory(DefaultThingSpec, factoryMock)
        instanceFactory.register(ModelType.of(ThingSpec), new SimpleModelRuleDescriptor("thing"))
            .withImplementation(ModelType.of(DefaultThingSpec))
        instanceFactory.register(ModelType.of(ManagedThingSpec), new SimpleModelRuleDescriptor("managed thing"))
            .withInternalView(ModelType.of(ManagedThingSpecInternalExtendingOtherThingSpec))

        when:
        instanceFactory.validateRegistrations()
        then:
        def ex = thrown IllegalStateException
        ex.message == "Factory registration for '${fullyQualifiedNameOf(ManagedThingSpec)}' is invalid because the default implementation type '${fullyQualifiedNameOf(DefaultThingSpec)}' does not implement unmanaged internal view '${fullyQualifiedNameOf(OtherThingSpec)}', internal view was registered by managed thing"
    }
}
