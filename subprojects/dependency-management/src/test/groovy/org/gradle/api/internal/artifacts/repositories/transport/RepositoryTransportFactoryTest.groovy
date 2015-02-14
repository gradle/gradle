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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.artifacts.repositories.DefaultPasswordCredentials
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.ResourceConnectorRepositoryTransport
import spock.lang.Specification
import spock.lang.Unroll

class RepositoryTransportFactoryTest extends Specification {

    def repositoryTransportFactory = new RepositoryTransportFactory(null, null, null, null, null, null)

    def "cannot create a transport for url with unsupported scheme"() {
        when:
        repositoryTransportFactory.createTransport(['unsupported'] as Set, null, null)

        then:
        InvalidUserDataException e = thrown()
        e.message == "Not a supported repository protocol 'unsupported': valid protocols are [file, http, https, sftp, s3]"
    }

    def "cannot creates a transport for mixed url scheme"() {
        when:
        repositoryTransportFactory.createTransport(['file', 'http'] as Set, null, null)

        then:
        InvalidUserDataException e = thrown()
        e.message == "You cannot mix different URL schemes for a single repository. Please declare separate repositories."
    }

    @Unroll
    def "should create a transport for [#scheme]"() {
        when:
        def transport = repositoryTransportFactory.createTransport([scheme] as Set, null, credentials)

        then:
        transport.class == expected

        where:
        scheme | credentials                                               || expected
        's3'   | new DefaultAwsCredentials(accessKey: 'a', secretKey: 's') || ResourceConnectorRepositoryTransport
        'http' | Mock(DefaultPasswordCredentials)                          || ResourceConnectorRepositoryTransport
        'sftp' | Mock(DefaultPasswordCredentials)                          || ResourceConnectorRepositoryTransport
    }

    def "should throw when credentials types is invalid"(){
        when:
        repositoryTransportFactory.convertPasswordCredentials(new DefaultAwsCredentials())

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Credentials must be an instance of: ${PasswordCredentials.class.getCanonicalName()}"
    }
}
