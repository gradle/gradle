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

package org.gradle.api.services.internal

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.ServiceReference
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.resources.SharedResourceLeaseRegistry
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultBuildServicesRegistryTest extends Specification {
    def listenerManager = new DefaultListenerManager(Scope.Build)
    def isolatableFactory = new DefaultIsolatableFactory(null, TestUtil.managedFactoryRegistry())
    def leaseRegistry = Stub(SharedResourceLeaseRegistry)
    def buildIdentifier = Mock(BuildIdentifier)
    def services = TestUtil.createTestServices { registrations ->
        registrations.addProvider(new ServiceRegistrationProvider() {
            @Provides
            BuildServiceRegistry createBuildServiceRegistry() {
                return new DefaultBuildServicesRegistry(
                    buildIdentifier,
                    services.get(DomainObjectCollectionFactory),
                    services.get(InstantiatorFactory),
                    services,
                    listenerManager,
                    isolatableFactory,
                    leaseRegistry,
                    BuildServiceProvider.Listener.EMPTY
                )
            }
        })
    }
    def registry = services.get(BuildServiceRegistry)

    def setup() {
        ServiceImpl.reset()
    }

    def "can lazily create service instance"() {
        when:
        def provider = registry.registerIfAbsent("service", ServiceImpl) {}

        then:
        ServiceImpl.instances.empty

        when:
        def service = provider.get()

        then:
        service instanceof ServiceImpl
        ServiceImpl.instances == [service]

        when:
        def service2 = provider.get()

        then:
        service2.is(service)
        ServiceImpl.instances == [service]
    }

    def "service provider always has value present"() {
        when:
        def provider = registry.registerIfAbsent("service", ServiceImpl) {}

        then:
        provider.present
        ServiceImpl.instances.empty
    }

    def "wraps and memoizes service instantiation failure"() {
        when:
        def provider = registry.registerIfAbsent("service", BrokenServiceImpl) {}

        then:
        noExceptionThrown()

        when:
        provider.get()

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to create service 'service'."
        e.cause.cause.is(BrokenServiceImpl.failure)
        BrokenServiceImpl.attempts == 1

        when:
        provider.get()

        then:
        def e2 = thrown(RuntimeException)
        e2.is(e)
        BrokenServiceImpl.attempts == 1
    }

    def "service has no max parallel usages by default"() {
        expect:
        registry.registerIfAbsent("service", ServiceImpl) {
            assert !it.maxParallelUsages.present
        }
        def registration = registry.registrations.getByName("service")
        !registration.maxParallelUsages.present
    }

    def "can locate registration by name"() {
        when:
        def provider = registry.registerIfAbsent("service", ServiceImpl) {}
        def registration = registry.registrations.getByName("service")

        then:
        registration.service.is(provider)
        ServiceImpl.instances.empty
    }

    def "reuses registration with same name"() {
        when:
        def provider1 = registry.registerIfAbsent("service", ServiceImpl) {}
        def provider2 = registry.registerIfAbsent("service", ServiceImpl) {}

        then:
        provider1.is(provider2)
    }

    def "can provide parameters to the service"() {
        when:
        def provider = registry.registerIfAbsent("service", ServiceImpl) {
            it.parameters.prop = "value"
        }
        def service = provider.get()

        then:
        service.prop == "value"
    }

    def "service can take no parameters"() {
        given:
        def action = Mock(Action)

        when:
        def provider = registry.registerIfAbsent("service", NoParamsServiceImpl, action)

        then:
        1 * action.execute(_)

        when:
        def service = provider.get()

        then:
        service != null
    }

    def "service can be create without action"() {
        when:
        def provider = registry.registerIfAbsent("service", NoParamsServiceImpl)
        def service = provider.get()

        then:
        service != null
    }

    def "can tweak parameters via the registration"() {
        when:
        def initialParameters
        def provider = registry.registerIfAbsent("service", ServiceImpl) {
            it.parameters.prop = "value 1"
            initialParameters = it.parameters
        }
        def parameters = registry.registrations.getByName("service").parameters

        then:
        parameters.is(initialParameters)
        parameters.prop == "value 1"

        when:
        parameters.prop = "value 2"
        def service = provider.get()

        then:
        service.prop == "value 2"
    }

    def "can tweak max parallel usage via the registration"() {
        when:
        registry.registerIfAbsent("service", BuildService) {
            it.maxParallelUsages = 42
        }
        def registration = registry.registrations.getByName("service")

        then:
        registration.maxParallelUsages.get() == 42
    }

    def "registration for service with no parameters is visible"() {
        when:
        registry.registerIfAbsent("service", NoParamsServiceImpl) {}

        then:
        registry.registrations.getByName("service") != null
    }

    def "registration for service with no action is visible"() {
        when:
        registry.registerIfAbsent("service", NoParamsServiceImpl)

        then:
        registry.registrations.getByName("service") != null
    }

    def "can use base service type to create a service with no state"() {
        when:
        def provider = registry.registerIfAbsent("service", BuildService) {}
        def service = provider.get()

        then:
        service instanceof BuildService
    }

    def "registration for service with no state is visible"() {
        when:
        registry.registerIfAbsent("service", BuildService) {}

        then:
        registry.registrations.getByName("service") != null
    }

    def "parameters are isolated when the service is instantiated"() {
        given:
        def provider = registry.registerIfAbsent("service", ServiceImpl) {
            it.parameters.prop = "value 1"
        }
        def parameters = registry.registrations.getByName("service").parameters
        def service = provider.get()

        when:
        parameters.prop = "ignore me 1"

        then:
        service.prop == "value 1"

        when:
        service.parameters.prop = "ignore me 2"

        then:
        parameters.prop == "ignore me 1"
    }

    def "stops service at end of build if it implements AutoCloseable"() {
        given:
        def provider1 = registry.registerIfAbsent("one", ServiceImpl) {}
        def provider2 = registry.registerIfAbsent("two", StoppableServiceImpl) {}
        def provider3 = registry.registerIfAbsent("three", StoppableServiceImpl) {}

        when:
        def notStoppable = provider1.get()
        def stoppable1 = provider2.get()
        def stoppable2 = provider3.get()

        then:
        ServiceImpl.instances == [notStoppable, stoppable1, stoppable2]

        when:
        buildFinished()

        then:
        ServiceImpl.instances == [notStoppable]
    }

    def "does not attempt to stop an unused service at the end of build"() {
        registry.registerIfAbsent("service", ServiceImpl) {}

        when:
        buildFinished()

        then:
        ServiceImpl.instances.empty
    }

    def "reports failure to stop service"() {
        given:
        def provider = registry.registerIfAbsent("service", BrokenStopServiceImpl) {}
        provider.get()

        when:
        buildFinished()

        then:
        def e = thrown(GradleException)
        e.message == "Failed to stop service 'service'."
        e.cause.is(BrokenStopServiceImpl.failure)
    }

    def "can locate resource corresponding to service registration"() {
        when:
        def service1 = registry.registerIfAbsent("service", BuildService) {
            it.maxParallelUsages = 42
        }
        def service2 = registry.registerIfAbsent("no-max", ServiceImpl) {}

        then:
        registry.forService(service1).maxUsages == 42
        registry.forService(service2).maxUsages == -1
    }

    def "cannot change max parallel usages once resource has been located"() {
        given:
        def service = registry.registerIfAbsent("service", ServiceImpl) {}
        def registration = registry.registrations.findByName("service")
        registration.maxParallelUsages = 42
        registry.forService(service)

        when:
        registration.maxParallelUsages = 4

        then:
        def e = thrown(IllegalStateException)
        e.message == "The value for property 'maxParallelUsages' is final and cannot be changed any further."

        and:
        registry.forService(service).maxUsages == 42
    }

    def "applies built-in convention to service references"() {
        given:
        def bean = services.get(ObjectFactory).newInstance(BeanWithBuildServiceProperty)

        expect:
        bean.getHello().getOrNull() == null

        when:
        registry.registerIfAbsent("helloService", NoParamsServiceImpl) {}

        then:
        bean.getHello().getOrNull() != null
    }

    private buildFinished() {
        listenerManager.getBroadcaster(BuildListener).buildFinished(Stub(BuildResult))
    }

    interface Params extends BuildServiceParameters {
        String getProp()

        void setProp(String value)
    }

    static abstract class ServiceImpl implements BuildService<Params> {
        static List<ServiceImpl> instances = []

        String getProp() {
            return getParameters().prop
        }

        static void reset() {
            instances.clear()
        }

        ServiceImpl() {
            instances.add(this)
        }
    }

    static abstract class StoppableServiceImpl extends ServiceImpl implements AutoCloseable {
        @Override
        void close() {
            instances.remove(this)
        }
    }

    static abstract class NoParamsServiceImpl implements BuildService<BuildServiceParameters.None> {

    }

    static abstract class BrokenServiceImpl implements BuildService<Params> {
        static int attempts = 0
        static RuntimeException failure = new RuntimeException("broken")

        BrokenServiceImpl() {
            attempts++
            throw failure
        }
    }

    static abstract class BrokenStopServiceImpl implements BuildService<Params>, AutoCloseable {
        static int attempts = 0
        static RuntimeException failure = new RuntimeException("broken")

        @Override
        void close() {
            attempts++
            throw failure
        }
    }

    static abstract class BeanWithBuildServiceProperty {
        @ServiceReference("helloService")
        abstract Property<NoParamsServiceImpl> getHello();
    }
}
