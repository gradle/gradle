/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.ProjectConfigurationException
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.provider.ProviderFactory
import spock.lang.Specification

class CredentialsProviderFactoryTest extends Specification {

    def providerFactory = Mock(ProviderFactory)
    def factory = new CredentialsProviderFactory(providerFactory)

    def "does not allow non-letters and non-digits for identity"() {
        when:
        factory.provide(PasswordCredentials, (String) identity)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Identity may contain only letters and digits, received: $identity"

        where:
        identity << ['%$#$#^!@', '', ' ', 'a ', 'a b', '$a', '_b', 'c!', '-42', null]
    }

    def "describes missing properties"() {
        given:
        providerFactory.gradleProperty('myServiceUsername') >> Providers.notDefined()
        providerFactory.gradleProperty('myServicePassword') >> Providers.notDefined()

        def provider = factory.provide(PasswordCredentials, 'myService')

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message.contains("The following Gradle properties are missing for 'myService' credentials:")
        e.message.contains("- myServiceUsername")
        e.message.contains("- myServicePassword")
    }

    def "does not throw on presence check when credentials are missing"() {
        given:
        providerFactory.gradleProperty('myServiceUsername') >> Providers.notDefined()
        providerFactory.gradleProperty('myServicePassword') >> Providers.notDefined()
        def provider = factory.provide(PasswordCredentials, 'myService')

        when:
        def isPresent = provider.isPresent()

        then:
        noExceptionThrown()
        !isPresent
    }

    def "describes single missing property"() {
        given:
        providerFactory.gradleProperty('myServiceUsername') >> Providers.notDefined()
        providerFactory.gradleProperty('myServicePassword') >> new DefaultProvider<>({ 'secret' })
        def provider = factory.provide(PasswordCredentials, 'myService')

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message.contains("The following Gradle properties are missing for 'myService' credentials:")
        e.message.contains("- myServiceUsername")
        !e.message.contains("- myServicePassword")
    }

    def "evaluates username and password credentials provider"() {
        given:
        providerFactory.gradleProperty('myServiceUsername') >> new DefaultProvider<>({ 'admin' })
        providerFactory.gradleProperty('myServicePassword') >> new DefaultProvider<>({ 'secret' })
        def provider = factory.provide(PasswordCredentials, 'myService')

        when:
        def credentials = provider.get()

        then:
        credentials instanceof PasswordCredentials
        credentials['username'] == 'admin'
        credentials['password'] == 'secret'
    }

    def "reuses a provider with same identity"() {
        expect:
        factory.provide(PasswordCredentials, 'id') == factory.provide(PasswordCredentials, 'id')
    }

    def "creates distinct providers for different identities"() {
        expect:
        factory.provide(PasswordCredentials, 'id') != factory.provide(PasswordCredentials, 'id2')
    }

    def "allows same identity for different credential types"() {
        expect:
        factory.provide(PasswordCredentials, 'id') != factory.provide(AwsCredentials, 'id')
    }

    def "does not require sessionToken for aws provider"() {
        given:
        providerFactory.gradleProperty('idAccessKey') >> new DefaultProvider<>({ 'access' })
        providerFactory.gradleProperty('idSecretKey') >> new DefaultProvider<>({ 'secret' })
        providerFactory.gradleProperty('idSessionToken') >> Providers.notDefined()
        def provider = factory.provide(AwsCredentials, "id")

        when:
        def credentials = provider.get()

        then:
        credentials.secretKey == 'secret'
        credentials.accessKey == 'access'
        credentials.sessionToken == null
    }

    def "allows setting sessionToken for aws provider"() {
        given:
        providerFactory.gradleProperty('idAccessKey') >> new DefaultProvider<>({ 'access' })
        providerFactory.gradleProperty('idSecretKey') >> new DefaultProvider<>({ 'secret' })
        providerFactory.gradleProperty('idSessionToken') >> new DefaultProvider<>({ 'token' })
        def provider = factory.provide(AwsCredentials, "id")

        when:
        def credentials = provider.get()

        then:
        credentials.secretKey == 'secret'
        credentials.accessKey == 'access'
        credentials.sessionToken == 'token'
    }

    def "collects multiple missing credentials failures when presence is checked"() {
        given:
        providerFactory.gradleProperty('cloudServiceAccessKey') >> Providers.notDefined()
        providerFactory.gradleProperty('cloudServiceSecretKey') >> Providers.notDefined()
        providerFactory.gradleProperty('cloudServiceSessionToken') >> Providers.notDefined()
        providerFactory.gradleProperty('myServiceUsername') >> Providers.notDefined()
        providerFactory.gradleProperty('myServicePassword') >> Providers.notDefined()
        def awsProvider = factory.provide(AwsCredentials, "cloudService")
        def passwordProvider = factory.provide(PasswordCredentials, "myService")

        when:
        awsProvider.isPresent()
        awsProvider.isPresent()
        passwordProvider.isPresent()

        and:
        factory.graphPopulated(null)

        then:
        def e = thrown(ProjectConfigurationException)
        e.message == 'Credentials required for this build could not be resolved.'
        e.causes.size() == 2
    }

    def "evaluates name and value of header credentials provider"() {
        given:
        providerFactory.gradleProperty('myServiceAuthHeaderName') >> new DefaultProvider<>({ 'Private-Token' })
        providerFactory.gradleProperty('myServiceAuthHeaderValue') >> new DefaultProvider<>({ 'secret' })
        def provider = factory.provide(HttpHeaderCredentials, 'myService')

        when:
        def credentials = provider.get()

        then:
        credentials instanceof HttpHeaderCredentials
        credentials['name'] == 'Private-Token'
        credentials['value'] == 'secret'
    }
}
