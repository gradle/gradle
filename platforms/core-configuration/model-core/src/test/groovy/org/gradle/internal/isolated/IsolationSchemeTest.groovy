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

package org.gradle.internal.isolated

import org.gradle.api.file.FileSystemOperations
import org.gradle.api.internal.parameters.NoneParameters
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.UnknownServiceException
import org.gradle.process.ExecOperations
import spock.lang.Specification

class IsolationSchemeTest extends Specification {
    def scheme = new IsolationScheme(SomeAction, SomeParams, Nothing)

    def "can extract parameters type"() {
        expect:
        scheme.parameterTypeFor(DirectUsage) == CustomParams
        scheme.parameterTypeFor(IndirectUsage) == CustomParams
        scheme.parameterTypeFor(ParameterizedType) == CustomParams
        scheme.parameterTypeFor(ComplexParameterizedType) == CustomParamsWithType
        scheme.parameterTypeFor(InheritedParameterizedType) == ExtendedCustomParams
        scheme.parameterTypeFor(FirstLevelInheritedParameterizedType) == ExtendedCustomParams

        scheme.parameterTypeFor(NoParams) == Nothing
    }

    def "fails when raw interface type is used as implementation"() {
        when:
        scheme.parameterTypeFor(SomeAction)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Could not create the parameters for SomeAction: an implementation type is required, but SomeAction is the SomeAction interface itself."
    }

    def "fails when base parameters type is used"() {
        when:
        scheme.parameterTypeFor(BaseParams)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Could not create the parameters for BaseParams: must use a sub-type of SomeParams as the parameters type. Use Nothing as the parameters type for implementations that do not take parameters."
    }

    def "fails when parameters type has not been declared"() {
        when:
        scheme.parameterTypeFor(RawActionType)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Could not create the parameters for RawActionType: must use a sub-type of SomeParams as the parameters type. Use Nothing as the parameters type for implementations that do not take parameters."
    }

    def "instantiates parameters via instantiator for non-no-params types"() {
        def custom = Stub(CustomParams)

        when:
        def result = scheme.instantiateParameters(CustomParams) { type -> custom }

        then:
        result.is(custom)
    }

    def "returns no-parameters singleton for the registered no-parameters type"() {
        when:
        def result = scheme.instantiateParameters(Nothing) { type -> throw new AssertionError("should not call instantiator") }

        then:
        result.is(NoneParameters.singletonOf(Nothing))
    }

    def "exposes authorized service"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)
        def service = Stub(serviceType)
        _ * allServices.find(serviceType) >> service

        def injectedServices = scheme.servicesForImplementation(params, allServices, [])

        when:
        def result = injectedServices.find(serviceType)

        then:
        result.is(service)

        when:
        def result2 = injectedServices.get(serviceType)

        then:
        result2.is(service)

        where:
        serviceType << [ExecOperations, FileSystemOperations, ObjectFactory, ProviderFactory]
    }

    def "does not expose allowed service when it is not available in backing registry"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)
        _ * allServices.find(serviceType) >> null

        def injectedServices = scheme.servicesForImplementation(params, allServices, [])

        when:
        def result = injectedServices.find(serviceType)

        then:
        result == null

        when:
        injectedServices.get(serviceType)

        then:
        def e = thrown(UnknownServiceException)
        e.message == "Services of type ${serviceType.simpleName} are not available for injection into instances of type SomeAction."

        where:
        serviceType << [ExecOperations, FileSystemOperations]
    }

    def "does not expose service that is not allowed"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)

        def injectedServices = scheme.servicesForImplementation(params, allServices, [])

        when:
        def result = injectedServices.find(Instantiator)

        then:
        result == null

        when:
        injectedServices.get(Instantiator)

        then:
        def e = thrown(UnknownServiceException)
        e.message == "Services of type Instantiator are not available for injection into instances of type SomeAction."
    }

    def "exposes parameters"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)

        def injectedServices = scheme.servicesForImplementation(params, allServices, [])

        when:
        def result = injectedServices.find(SomeParams)

        then:
        result.is(params)

        when:
        def result2 = injectedServices.get(SomeParams)

        then:
        result2.is(params)
    }

    def "exposes None singleton as parameters service"() {
        def allServices = Mock(ServiceLookup)

        def injectedServices = scheme.servicesForImplementation(NoneParameters.singletonOf(Nothing), allServices, [])

        when:
        def result = injectedServices.find(SomeParams)

        then:
        result.is(NoneParameters.singletonOf(Nothing))

        when:
        def result2 = injectedServices.get(SomeParams)

        then:
        result2.is(NoneParameters.singletonOf(Nothing))
        result2.is(result)
    }
}

interface SomeParams {
}

final class Nothing extends NoneParameters implements SomeParams {
    private Nothing() {}
}

interface SomeAction<P extends SomeParams> {
}

interface CustomParams extends SomeParams {
}

interface CustomParamsWithType<T> extends SomeParams {
}

interface ExtendedCustomParams extends CustomParams {
}

class DirectUsage implements SomeAction<CustomParams> {
}

interface Indirect<P> extends SomeAction<CustomParams> {
}

class IndirectUsage implements Indirect<Number> {
}

class NoParams implements SomeAction<Nothing> {
}

class BaseParams implements SomeAction<SomeParams> {
}

class RawActionType implements SomeAction {
}

class ComplexParameterizedType<A extends CustomParamsWithType<?>, B extends A, C extends B> implements SomeAction<C> {
}

class ParameterizedType<T extends CustomParams> implements SomeAction<T> {
}

class InheritedParameterizedType extends ParameterizedType<ExtendedCustomParams> {
}

class ThirdLevelInheritedParameterizedType<B extends SomeParams> implements SomeAction<B> {
}

class SecondLevelInheritedParameterizedType<A extends CustomParams> extends ThirdLevelInheritedParameterizedType<A> {
}

class FirstLevelInheritedParameterizedType extends SecondLevelInheritedParameterizedType<ExtendedCustomParams> {
}
