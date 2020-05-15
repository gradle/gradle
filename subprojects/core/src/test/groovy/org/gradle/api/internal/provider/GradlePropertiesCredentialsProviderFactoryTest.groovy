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


import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.properties.GradleProperties
import spock.lang.Specification

class GradlePropertiesCredentialsProviderFactoryTest extends Specification {

    def gradleProperties = Mock(GradleProperties)
    def taskExecutionGraph = Mock(TaskExecutionGraph)
    def factory = new GradlePropertiesCredentialsProviderFactory(gradleProperties, taskExecutionGraph)

    def "does not allow non-letters and non-digits for identity"() {
        when:
        factory.usernameAndPassword(identity)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Identity may contain only letters and digits, received: $identity"

        where:
        identity << ['%$#$#^!@', '', ' ', 'a ', 'a b', '$a', '_b', 'c!', '-42', null]
    }

    def "describes missing properties"() {
        given:
        def provider = factory.usernameAndPassword('myService')

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message.contains("Cannot query the value of username and password provider because it has no value available.")
        e.message.contains("The value of this provider is derived from:")
        e.message.contains("- Gradle property 'myServicePassword'")
        e.message.contains("- Gradle property 'myServiceUsername'")
    }

    def "describes single missing property"() {
        given:
        gradleProperties.find('myServicePassword') >> 'secret'
        def provider = factory.usernameAndPassword('myService')

        when:
        provider.get()

        then:
        def e = thrown(MissingValueException)
        e.message.contains("Cannot query the value of username and password provider because it has no value available.")
        e.message.contains("The value of this provider is derived from:")
        !e.message.contains("- Gradle property 'myServicePassword'")
        e.message.contains("- Gradle property 'myServiceUsername'")
    }

    def "evaluates username and password credentials provider"() {
        given:
        gradleProperties.find('myServiceUsername') >> 'admin'
        gradleProperties.find('myServicePassword') >> 'secret'
        def provider = factory.usernameAndPassword('myService')

        when:
        def credentials = provider.get()

        then:
        credentials instanceof PasswordCredentials
        credentials['username'] == 'admin'
        credentials['password'] == 'secret'
    }

    def "reuses username and password provider with same identity"() {
        expect:
        factory.usernameAndPassword('id') == factory.usernameAndPassword('id')
    }

    def "creates distinct username and password providers for different identities"() {
        expect:
        factory.usernameAndPassword('id') != factory.usernameAndPassword('id2')
    }

}
