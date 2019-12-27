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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceLookup
import org.gradle.internal.service.ServiceLookupException
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

        scheme.parameterTypeFor(NoParams) == null
        scheme.parameterTypeFor(SomeAction) == null
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

    def "exposes whitelisted service"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)
        def service = Stub(serviceType)
        _ * allServices.find(serviceType) >> service

        def injectedServices = scheme.servicesForImplementation(params, allServices)

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

    def "does not expose white-listed service when it is not available in backing registry"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)
        _ * allServices.find(serviceType) >> null

        def injectedServices = scheme.servicesForImplementation(params, allServices)

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

    def "does not expose service that is not white-listed"() {
        def allServices = Mock(ServiceLookup)
        def params = Stub(SomeParams)

        def injectedServices = scheme.servicesForImplementation(params, allServices)

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

        def injectedServices = scheme.servicesForImplementation(params, allServices)

        when:
        def result = injectedServices.find(SomeParams)

        then:
        result.is(params)

        when:
        def result2 = injectedServices.get(SomeParams)

        then:
        result2.is(params)
    }

    def "cannot query parameters when parameters are null"() {
        def allServices = Mock(ServiceLookup)

        def injectedServices = scheme.servicesForImplementation(null, allServices)

        when:
        injectedServices.find(SomeParams)

        then:
        def e = thrown(ServiceLookupException)
        e.message == "Cannot query the parameters of an instance of SomeAction that takes no parameters."

        when:
        injectedServices.get(SomeParams)

        then:
        def e2 = thrown(ServiceLookupException)
        e2.message == "Cannot query the parameters of an instance of SomeAction that takes no parameters."
    }
}

interface SomeParams {
}

interface Nothing extends SomeParams {}

interface SomeAction<P extends SomeParams> {
}

interface CustomParams extends SomeParams {
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

class ParameterizedType<T extends CustomParams> implements SomeAction<T> {
}
