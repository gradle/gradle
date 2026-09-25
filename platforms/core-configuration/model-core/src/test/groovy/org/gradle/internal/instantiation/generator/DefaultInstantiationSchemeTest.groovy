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

package org.gradle.internal.instantiation.generator

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.InjectedServicesPolicy
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.internal.service.ServiceLookup
import spock.lang.Specification

import javax.inject.Inject

class DefaultInstantiationSchemeTest extends Specification {
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def classGenerator = AsmBackedClassGenerator.injectOnly([], Stub(PropertyRoleAnnotationHandler), [], cacheFactory, 123)
    def scheme = new DefaultInstantiationScheme(
        new Jsr330ConstructorSelector(classGenerator, cacheFactory.newClassCache()),
        classGenerator,
        new DefaultServiceRegistry(),
        [] as Set,
        new TestCrossBuildInMemoryCacheFactory())

    def "can specify a set of services to inject"() {
        def services = Mock(ServiceLookup)
        _ * services.get(String) >> "value"

        when:
        def value = scheme.withServices(services).instantiator().newInstance(WithServices)

        then:
        value.prop == "value"
    }

    def "applies the policy to the services injected into properties before creating an instance"() {
        def policy = Mock(InjectedServicesPolicy)
        def services = Mock(ServiceLookup)
        _ * services.get(String) >> "value"

        when:
        def value = scheme.withInjectedServicesPolicy(policy).withServices(services).forType(WithServices).newInstance()

        then:
        1 * policy.declaredInjectedServices(WithServices, [(WithServices): [String]])
        value.prop == "value"
    }

    def "applies the policy to the services injected into the constructor"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(WithConstructorServices)

        then:
        1 * policy.declaredInjectedServices(WithConstructorServices, [(WithConstructorServices): [String, Number]])
    }

    def "reports constructor parameters under the declaring type with its property injections for #type.simpleName"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(type)

        then:
        1 * policy.declaredInjectedServices(type, injectedServicesByDeclaringType)

        where:
        type                               | injectedServicesByDeclaringType
        WithConstructorAndPropertyServices | [(WithConstructorAndPropertyServices): [String, Number]]
        WithNestedConstructorServices      | [(WithConstructorServices): [String, Number]]
    }

    def "applies the policy to the services injected through nested managed types"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(WithNested)

        then:
        1 * policy.declaredInjectedServices(WithNested, [(WithServices): [String]])
    }

    def "inspects a type nesting itself once"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(SelfNesting)

        then:
        1 * policy.declaredInjectedServices(SelfNesting, [(SelfNesting): [String]])
    }

    def "does not inspect the values of managed properties"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(WithManagedProperties)

        then:
        1 * policy.declaredInjectedServices(WithManagedProperties, [:])
    }

    def "applies the policy every time a type is prepared for instantiation"() {
        def policy = Mock(InjectedServicesPolicy)
        def schemeWithPolicy = scheme.withInjectedServicesPolicy(policy)

        when:
        schemeWithPolicy.forType(WithServices)
        schemeWithPolicy.forType(WithServices)
        schemeWithPolicy.withServices(Stub(ServiceLookup)).forType(WithServices)

        then:
        3 * policy.declaredInjectedServices(WithServices, _)
    }

    def "does not apply the policy to a type that cannot be instantiated"() {
        def policy = Mock(InjectedServicesPolicy)

        when:
        scheme.withInjectedServicesPolicy(policy).forType(Inner)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Class DefaultInstantiationSchemeTest.Inner is a non-static inner class."
        0 * policy._
    }

    def "does not create an instance of a type the policy rejects"() {
        def policy = Mock(InjectedServicesPolicy)
        def failure = new IllegalArgumentException("rejected")
        _ * policy.declaredInjectedServices(RejectedWithServices, _) >> { throw failure }

        when:
        scheme.withInjectedServicesPolicy(policy).forType(RejectedWithServices).newInstance()

        then:
        def e = thrown(IllegalArgumentException)
        e.is(failure)
    }

    def "does not apply the policy to the scheme it was derived from"() {
        def policy = Mock(InjectedServicesPolicy)
        scheme.withInjectedServicesPolicy(policy)

        when:
        scheme.forType(WithServices)

        then:
        0 * policy._
    }

    def "can create instances without invoking their constructor to use for deserialization"() {
        when:
        def value = scheme.deserializationInstantiator().newInstance(Impl, Base)

        then:
        value.prop == "default"
    }

    def "can inject services into instances created for deserialization"() {
        def services = Mock(ServiceLookup)
        _ * services.get(String) >> "value"

        when:
        def value = scheme.withServices(services).deserializationInstantiator().newInstance(WithServices, Object)

        then:
        value.prop == "value"
    }

    static class Base {
        String prop

        Base() {
            prop = "default"
        }
    }

    static abstract class Impl extends Base {
        Impl() {
            throw new RuntimeException("should not be called")
        }
    }

    static abstract class WithServices {
        @Inject
        abstract String getProp()
    }

    static abstract class RejectedWithServices {
        RejectedWithServices() {
            throw new RuntimeException("should not be created")
        }

        @Inject
        abstract String getProp()
    }

    class Inner {
    }

    static class WithConstructorServices {
        @Inject
        WithConstructorServices(String prop, Number other) {
        }
    }

    static abstract class WithConstructorAndPropertyServices {
        @Inject
        WithConstructorAndPropertyServices(String prop) {
        }

        @Inject
        abstract Number getNumber()
    }

    static abstract class WithNestedConstructorServices {
        @Nested
        abstract WithConstructorServices getNested()
    }

    static abstract class WithNested {
        @Nested
        abstract WithServices getNested()
    }

    interface SelfNesting {
        @Nested
        SelfNesting getNested()

        @Inject
        String getProp()
    }

    interface WithManagedProperties {
        Property<String> getProp()

        ListProperty<String> getList()

        ConfigurableFileCollection getFiles()

        @Nested
        Property<String> getNestedProp()
    }
}
