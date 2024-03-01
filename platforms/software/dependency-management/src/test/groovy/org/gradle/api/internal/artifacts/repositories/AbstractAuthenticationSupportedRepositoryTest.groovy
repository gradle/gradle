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
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor
import org.gradle.authentication.Authentication
import org.gradle.internal.authentication.DefaultAuthenticationContainer
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.ClosureBackedAction
import org.gradle.util.TestUtil
import spock.lang.Specification

class AbstractAuthenticationSupportedRepositoryTest extends Specification {

    AuthSupportedRepository repo() {
        new AuthSupportedRepository(TestUtil.instantiatorFactory().decorateLenient(), new DefaultAuthenticationContainer(TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP))
    }

    def "should configure default password credentials using an action only"() {
        setup:
        DefaultPasswordCredentials passwordCredentials = new DefaultPasswordCredentials()
        enhanceCredentials(passwordCredentials, 'username', 'password')

        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Stub()
        instantiator.newInstance(DefaultPasswordCredentials) >> passwordCredentials

        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)

        def configAction = new Action<PasswordCredentials>() {
            @Override
            void execute(PasswordCredentials credentials) {
                credentials.username = "myUsername"
                credentials.password = "myPassword"
            }
        }

        when:
        repo.credentials(configAction)

        then:
        repo.getCredentials(PasswordCredentials.class)
        repo.getCredentials(PasswordCredentials.class).username == 'myUsername'
        repo.getCredentials(PasswordCredentials.class).password == 'myPassword'
    }

    def "getCredentials(Class) instantiates credentials if not yet configured"() {
        given:
        DefaultAwsCredentials enhancedCredentials = new DefaultAwsCredentials()
        enhanceCredentials(enhancedCredentials, 'accessKey', 'secretKey')

        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Mock()
        instantiator.newInstance(DefaultAwsCredentials) >> enhancedCredentials

        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)

        def action = new ClosureBackedAction<DefaultAwsCredentials>({
            accessKey = 'key'
            secretKey = 'secret'
        })

        expect:
        repo.getCredentials(AwsCredentials.class) instanceof AwsCredentials

    }


    def "getCredentials(Class) instantiates the correct credential types "() {
        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)

        when:
        repo.getCredentials(credentialType) == credentials

        then:
        1 * instantiator.newInstance(_) >> credentials

        where:
        credentialType      | credentials
        AwsCredentials      | Mock(AwsCredentials)
        PasswordCredentials | Mock(PasswordCredentials)
    }

    def "getCredentials(Class) throws IllegalArgumentException when setting credentials with different type than already set"() {
        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)
        1 * instantiator.newInstance(_) >> credentials

        when:
        repo.getCredentials(AwsCredentials)
        and:
        repo.getCredentials(PasswordCredentials)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Given credentials type '$PasswordCredentials.name' does not match actual type '$AwsCredentials.name'"

        where:
        credentials << Mock(AwsCredentials)
    }

    def "getCredentials(Class) throws IllegalArgumentException when setting credentials with unknown type"() {
        AuthSupportedRepository repo = new AuthSupportedRepository(Mock(Instantiator), Mock(AuthenticationContainer))
        when:
        repo.getCredentials(UnsupportedCredentials)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Unknown credentials type: '$UnsupportedCredentials.name' (supported types: $PasswordCredentials.name, $AwsCredentials.name and $HttpHeaderCredentials.name)."
    }

    def "credentials(Class, Action) creates credentials on demand if required"() {
        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Stub()

        Action action = Mock()
        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)

        when:
        repo.credentials(credentialType, action)

        then:
        1 * instantiator.newInstance(_) >> credentials
        1 * action.execute(credentials)
        repo.getCredentials(credentialType) == credentials

        where:
        credentialType      | credentials
        AwsCredentials      | Mock(AwsCredentials)
        PasswordCredentials | Mock(PasswordCredentials)
    }

    def "can reference alternative credentials"() {
        given:
        Instantiator instantiator = Mock()
        AuthenticationContainer authenticationContainer = Stub()

        Action action = Mock()
        def credentials = Mock(AwsCredentials)
        1 * instantiator.newInstance(_) >> credentials
        1 * action.execute(credentials)

        AuthSupportedRepository repo = new AuthSupportedRepository(instantiator, authenticationContainer)
        when:
        repo.credentials(AwsCredentials, action)

        then:
        repo.getCredentials(AwsCredentials) instanceof AwsCredentials
    }

    def "get credentials throws ISE if not using password credentials"() {
        when:
        repo().with {
            credentials(AwsCredentials, {})
            credentials
        }

        then:
        thrown IllegalStateException
    }

    def "credentials(Action) throws ISE if not using password credentials"() {
        when:
        repo().with {
            credentials(AwsCredentials, {})
            credentials {}
        }

        then:
        thrown IllegalStateException
    }

    def "authentication container throws IUD with authentication of unknown type"() {
        def action = new Action<AuthenticationContainer>() {
            @Override
            void execute(AuthenticationContainer authentications) {
                authentications.create('basic', UnsupportedAuthentication)
            }
        }

        when:
        repo().with {
            authentication(action)
        }

        then:
        thrown InvalidUserDataException
    }

    private void enhanceCredentials(Credentials credentials, String... props) {
        props.each { prop ->
            credentials.metaClass."$prop" = { String val ->
                delegate."set${prop.capitalize()}"(val)
            }
        }
    }

    class AuthSupportedRepository extends AbstractAuthenticationSupportedRepository {
        AuthSupportedRepository(Instantiator instantiator, AuthenticationContainer authenticationContainer) {
            super(instantiator, authenticationContainer, TestUtil.objectFactory(), null, new VersionParser())
        }

        @Override
        protected RepositoryDescriptor createDescriptor() {
            throw new UnsupportedOperationException()
        }

        @Override
        ConfiguredModuleComponentRepository createResolver() {
            throw new UnsupportedOperationException()
        }
    }

    interface UnsupportedCredentials extends Credentials {}

    interface UnsupportedAuthentication extends Authentication {}
}
