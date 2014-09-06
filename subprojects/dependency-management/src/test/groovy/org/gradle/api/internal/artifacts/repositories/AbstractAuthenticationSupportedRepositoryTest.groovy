/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.AwsCredentials
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.credentials.Credentials
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification
import spock.lang.Unroll

class AbstractAuthenticationSupportedRepositoryTest extends Specification {

    final AbstractAuthenticationSupportedRepository repository = new AuthSupportedRepository(null, new DirectInstantiator())

    def "should configure default password credentials using a closure only"() {
        setup:
        DefaultPasswordCredentials passwordCredentials = new DefaultPasswordCredentials()
        enhanceCredentials(passwordCredentials, 'username', 'password')

        Instantiator instantiator = Mock()
        instantiator.newInstance(DefaultPasswordCredentials) >> passwordCredentials

        AuthSupportedRepository repo = new AuthSupportedRepository(passwordCredentials, instantiator)

        Closure cls = {
            username "myUsername"
            password "myPassword"
        }

        when:
        repo.credentials(cls)

        then:
        repo.credentials
        repo.credentials.username == 'myUsername'
        repo.credentials.password == 'myPassword'
    }

    def "should configure alternative credentials"() {
        setup:
        DefaultAwsCredentials enhancedCredentials = new DefaultAwsCredentials()
        enhanceCredentials(enhancedCredentials, 'accessKey', 'secretKey')

        Instantiator instantiator = Mock()
        instantiator.newInstance(DefaultAwsCredentials) >> enhancedCredentials

        AuthSupportedRepository repo = new AuthSupportedRepository(null, instantiator)

        def action = new ClosureBackedAction<DefaultAwsCredentials>({
            accessKey = 'key'
            secretKey = 'secret'
        })

        when:
        repo.credentials(AwsCredentials.class, action)
        def credentials = repo.getAlternativeCredentials()

        then:
        credentials.accessKey == 'key'
        credentials.secretKey == 'secret'

    }

    @Unroll
    def "should instantiate the correct default credential types "() {
        Instantiator instantiator = Mock()
        Action action = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(null, instantiator)

        when:
        repo.credentials(credentialType, action) == credentials

        then:
        1 * instantiator.newInstance(_) >> credentials
        1 * action.execute(credentials)

        where:
        credentialType      | credentials
        AwsCredentials      | Mock(AwsCredentials)
        PasswordCredentials | Mock(PasswordCredentials)
    }

    def "should throw when credentials have been pre-configured with a specific type"() {
        Instantiator instantiator = Mock()
        Action action = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(null, instantiator)
        1 * instantiator.newInstance(_) >> credentials
        1 * action.execute(credentials)

        when:
        repo.credentials(credentialType, action)
        repo.getCredentials()

        then:
        def ex = thrown(IllegalStateException)
        ex.message.startsWith("Password credentials has been overridden by a specific credentials of type [")

        where:
        credentialType      | credentials
        AwsCredentials      | Mock(AwsCredentials)
        PasswordCredentials | Mock(PasswordCredentials)
    }

    def "should configure aws credentials"() {
        Instantiator instantiator = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(null, instantiator)
        def alternative = Mock(AwsCredentials)
        Action action = Mock()
        1 * instantiator.newInstance(DefaultAwsCredentials) >> alternative
        repo.credentials(AwsCredentials, action)

        when:
        def credentials = repo.getAlternativeCredentials()

        then:
        credentials == alternative
    }

    def "should default both credentials to PasswordCredentials"() {
        setup:
        PasswordCredentials pCredentials = Mock()
        when:
        def repo = new AuthSupportedRepository(pCredentials, null)
        then:
        repo.getCredentials() == pCredentials
        repo.getAlternativeCredentials() == pCredentials
    }

    def "Should throw on configuring and password credentials is null"() {
        setup:
        def repo = new AuthSupportedRepository(null, new DirectInstantiator())
        def action = new ClosureBackedAction<DefaultPasswordCredentials>({
            username = 'key'
            password = 'secret'
        })

        when:
        repo.credentials(action)
        then:
        def ex = thrown(IllegalStateException)
        ex.message == "Password credentials is null, most likely an alternative credentials type has been configured for this repository"
    }

    private void enhanceCredentials(Credentials credentials, String... props) {
        props.each { prop ->
            credentials.metaClass."$prop" = { String val ->
                delegate."set${prop.capitalize()}"(val)
            }
        }
    }

    class AuthSupportedRepository extends AbstractAuthenticationSupportedRepository {
        AuthSupportedRepository(Credentials credentials, Instantiator instantiator) {
            super(credentials, instantiator)
        }
    }
}
