/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.transport

import com.google.common.collect.Lists
import org.gradle.StartParameter
import org.gradle.api.InvalidUserDataException
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride
import org.gradle.authentication.Authentication
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.authentication.AbstractAuthentication
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.transport.ResourceConnectorRepositoryTransport
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.util.TestUtil
import spock.lang.Specification

class RepositoryTransportFactoryTest extends Specification {

    def connectorFactory1 = Mock(ResourceConnectorFactory)
    def connectorFactory2 = Mock(ResourceConnectorFactory)
    def producerGuard = Mock(ProducerGuard)
    RepositoryTransportFactory repositoryTransportFactory

    def setup() {
        connectorFactory1.getSupportedProtocols() >> (["protocol1"] as Set)
        connectorFactory1.getSupportedAuthentication() >> ([GoodCredentialsAuthentication, BadCredentialsAuthentication, AuthenticationWithoutCredentials] as Set)
        connectorFactory2.getSupportedProtocols() >> (["protocol2a", "protocol2b"] as Set)
        connectorFactory2.getSupportedAuthentication() >> ([] as Set)
        List<ResourceConnectorFactory> resourceConnectorFactories = Lists.newArrayList(connectorFactory1, connectorFactory2)
        StartParameterResolutionOverride override = new StartParameterResolutionOverride(new StartParameter(), new File("dummy"))
        repositoryTransportFactory = new RepositoryTransportFactory(resourceConnectorFactories, null, null, null, null, null, override, producerGuard, Mock(FileResourceRepository), TestUtil.checksumService)
    }

    RepositoryTransport createTransport(Set<String> schemes, String name, Collection<Authentication> authentications) {
        return repositoryTransportFactory.createTransport(schemes, name, authentications, Mock(HttpRedirectVerifier))
    }

    def "cannot create a transport for url with unsupported scheme"() {
        when:
        createTransport(['unsupported'] as Set, null, [])

        then:
        InvalidUserDataException e = thrown()
        e.message == "Not a supported repository protocol 'unsupported': valid protocols are [protocol1, protocol2a, protocol2b]"
    }

    def "cannot creates a transport for mixed url scheme"() {
        when:
        createTransport(['protocol1', 'protocol2b'] as Set, null, [])

        then:
        InvalidUserDataException e = thrown()
        e.message == "You cannot mix different URL schemes for a single repository. Please declare separate repositories."
    }

    def "should create a transport for known scheme"() {
        def authentication = new GoodCredentialsAuthentication('good')
        authentication.credentials = Mock(GoodCredentials)

        when:
        def transport = createTransport(['protocol1'] as Set, null, [authentication])

        then:
        transport.class == ResourceConnectorRepositoryTransport
    }

    def "should create transport for known scheme, authentication and credentials"() {
        def authentication = new GoodCredentialsAuthentication('good')
        authentication.credentials = Mock(GoodCredentials)

        when:
        def transport = createTransport(['protocol1'] as Set, null, [authentication])

        then:
        transport.class == ResourceConnectorRepositoryTransport
    }

    def "should throw when using invalid authentication type"() {
        def credentials = Mock(GoodCredentials)
        authentication.credentials = credentials

        when:
        createTransport(protocols as Set, null, [authentication])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Authentication scheme ${authentication} is not supported by protocol '${protocols[0]}'"

        where:
        authentication                            | protocols
        new NoCredentialsAuthentication('none')   | ['protocol1']
        new GoodCredentialsAuthentication('good') | ['protocol2a', 'protocol2b']
    }

    def "should throw when using invalid credentials type"() {
        authentication*.credentials = credentials

        when:
        createTransport(['protocol1'] as Set, null, authentication)

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "Credentials type of '${credentials.class.simpleName}' is not supported by authentication scheme ${failingAuthentication}"

        where:
        credentials           | authentication                                                                       | failingAuthentication
        Mock(BadCredentials)  | [new GoodCredentialsAuthentication('good')]                                          | "'good'(Authentication)"
        Mock(GoodCredentials) | [new GoodCredentialsAuthentication('good'), new BadCredentialsAuthentication('bad')] | "'bad'(Authentication)"
    }

    def "should throw when specifying authentication types with null credentials"() {
        when:
        createTransport(['protocol1'] as Set, null, [new GoodCredentialsAuthentication('good')])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "You cannot configure authentication schemes for this repository type if no credentials are provided."
    }

    def "should accept no credentials for auth"() {
        when:
        def transport = repositoryTransportFactory.createTransport(['protocol1'] as Set, null, [new AuthenticationWithoutCredentials('good')], Mock(HttpRedirectVerifier))

        then:
        transport.class == ResourceConnectorRepositoryTransport
    }

    def "should throw when specifying multiple authentication schemes of the same type"() {
        def authentication = new GoodCredentialsAuthentication('good')
        authentication.credentials = Mock(GoodCredentials)

        when:
        createTransport(['protocol1'] as Set, null, [authentication, authentication])

        then:
        def ex = thrown(InvalidUserDataException)
        ex.message == "You cannot configure multiple authentication schemes of the same type.  The duplicate one is 'good'(Authentication)."
    }

    private class AuthenticationWithoutCredentials extends AbstractAuthentication {
        AuthenticationWithoutCredentials(String name) {
            super(name, Authentication, GoodCredentials)
        }

        boolean requiresCredentials() {
            return false;
        }

    }

    private class GoodCredentialsAuthentication extends AbstractAuthentication {
        GoodCredentialsAuthentication(String name) {
            super(name, Authentication, GoodCredentials)
        }

        boolean requiresCredentials() {
            return true;
        }

    }

    private class BadCredentialsAuthentication extends AbstractAuthentication {
        BadCredentialsAuthentication(String name) {
            super(name, Authentication, BadCredentials)
        }

        boolean requiresCredentials() {
            return true;
        }

    }

    private class NoCredentialsAuthentication extends AbstractAuthentication {
        NoCredentialsAuthentication(String name) {
            super(name, Authentication)
        }

        boolean requiresCredentials() {
            return false;
        }


    }

    private interface GoodCredentials extends Credentials {}

    private interface BadCredentials extends Credentials {}
}
