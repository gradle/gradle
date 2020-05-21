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
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.internal.properties.GradleProperties
import spock.lang.Specification

class GradlePropertiesCredentialsProviderFactoryTest extends Specification {

    def gradleProperties = Mock(GradleProperties)
    def factory = new GradlePropertiesCredentialsProviderFactory(gradleProperties)

    def "does not allow non-letters and non-digits for identity"() {
        when:
        factory.provideCredentials(PasswordCredentials, (String) identity)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Identity may contain only letters and digits, received: $identity"

        where:
        identity << ['%$#$#^!@', '', ' ', 'a ', 'a b', '$a', '_b', 'c!', '-42', null]
    }

    def "describes missing properties"() {
        given:
        def provider = factory.provideCredentials(PasswordCredentials, 'myService')

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
        def provider = factory.provideCredentials(PasswordCredentials, 'myService')

        when:
        def isPresent = provider.isPresent()

        then:
        noExceptionThrown()
        !isPresent
    }

    def "describes single missing property"() {
        given:
        gradleProperties.find('myServicePassword') >> 'secret'
        def provider = factory.provideCredentials(PasswordCredentials, 'myService')

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
        gradleProperties.find('myServiceUsername') >> 'admin'
        gradleProperties.find('myServicePassword') >> 'secret'
        def provider = factory.provideCredentials(PasswordCredentials, 'myService')

        when:
        def credentials = provider.get()

        then:
        credentials instanceof PasswordCredentials
        credentials['username'] == 'admin'
        credentials['password'] == 'secret'
    }

    def "reuses a provider with same identity"() {
        expect:
        factory.provideCredentials(PasswordCredentials, 'id') == factory.provideCredentials(PasswordCredentials, 'id')
    }

    def "creates distinct providers for different identities"() {
        expect:
        factory.provideCredentials(PasswordCredentials, 'id') != factory.provideCredentials(PasswordCredentials, 'id2')
    }

    def "allows same identity for different credential types"() {
        expect:
        factory.provideCredentials(PasswordCredentials, 'id') != factory.provideCredentials(AwsCredentials, 'id')
    }

    def "does not require sessionToken for aws provider"() {
        given:
        gradleProperties.find('idAccessKey') >> 'access'
        gradleProperties.find('idSecretKey') >> 'secret'
        def provider = factory.provideCredentials(AwsCredentials, "id")

        when:
        def credentials = provider.get()

        then:
        credentials.secretKey == 'secret'
        credentials.accessKey == 'access'
        credentials.sessionToken == null
    }

    def "allows setting sessionToken for aws provider"() {
        given:
        gradleProperties.find('idAccessKey') >> 'access'
        gradleProperties.find('idSecretKey') >> 'secret'
        gradleProperties.find('idSessionToken') >> 'token'
        def provider = factory.provideCredentials(AwsCredentials, "id")

        when:
        def credentials = provider.get()

        then:
        credentials.secretKey == 'secret'
        credentials.accessKey == 'access'
        credentials.sessionToken == 'token'
    }

    def "collects multiple missing credentials failures when presence is checked"() {
        given:
        def awsProvider = factory.provideCredentials(AwsCredentials, "cloudService")
        def passwordProvider = factory.provideCredentials(AwsCredentials, "myService")

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
}
