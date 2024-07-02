/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.provider.DefaultProviderFactory
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Provider
import org.gradle.internal.credentials.DefaultPasswordCredentials
import spock.lang.Specification

class DefaultGradleApiSourcesResolverTest extends Specification {
    StoredRepositoryRef resultRef

    MavenArtifactRepository repository

    def setup() {
        resultRef = new StoredRepositoryRef()
        repository = Mock()

        repository.setUrl(_ as Object) >> { args ->
            resultRef.url = args[0] as String
        }

        repository.setUrl(_ as URI) >> { args ->
            resultRef.url = args[0].toString()
        }

        repository.credentials(_ as Action) >> { args ->
            def storedCredentials = new DefaultPasswordCredentials()
            resultRef.credentials = storedCredentials
            args[0].execute(storedCredentials)
        }
    }

    def "configureLibsRepo with default values"() {
        def providers = new FixedTestProviderFactory([:], [:])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == DefaultGradleApiSourcesResolver.GRADLE_LIBS_REPO_URL
        resultRef.credentials == null
    }

    def "configureLibsRepo with default url ignores credentials "() {
        def providers = new FixedTestProviderFactory([:], [
            'org.gradle.libraries.sourceRepository.credentialsProperties' : 'testUser:testPassword',
            'testUser' : 'test-secret-user',
            'testPassword' : 'test-secret-password',
        ])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == DefaultGradleApiSourcesResolver.GRADLE_LIBS_REPO_URL
        resultRef.credentials == null
    }

    def "configureLibsRepo with environment without credentials"() {
        def expectedUrl = 'https://example.com/test'
        def providers = new FixedTestProviderFactory(['GRADLE_LIBS_REPO_OVERRIDE' : expectedUrl], [:])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == expectedUrl
        resultRef.credentials == null
    }

    def "configureLibsRepo with environment with credentials"() {
        def expectedUrl = 'https://example.com/test'
        def expectedUser = 'expected-test-user'
        def expectedPassword = 'expected-test-password'
        def providers = new FixedTestProviderFactory(['GRADLE_LIBS_REPO_OVERRIDE' : expectedUrl], [
            'org.gradle.libraries.sourceRepository.credentialsProperties' : 'testUser:testPassword',
            'testUser' : expectedUser,
            'testPassword' : expectedPassword,
        ])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == expectedUrl
        resultRef.credentials != null
        resultRef.credentials.username == expectedUser
        resultRef.credentials.password == expectedPassword
    }

    def "configureLibsRepo with Gradle properties without credentials"() {
        def expectedUrl = 'https://example.com/test'
        def providers = new FixedTestProviderFactory([:], ['org.gradle.libraries.sourceRepository.url' : expectedUrl])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == expectedUrl
        resultRef.credentials == null
    }

    def "configureLibsRepo with Gradle properties with credentials"() {
        def expectedUrl = 'https://example.com/test'
        def expectedUser = 'expected-test-user'
        def expectedPassword = 'expected-test-password'
        def providers = new FixedTestProviderFactory([:], [
            'org.gradle.libraries.sourceRepository.url' : expectedUrl,
            'org.gradle.libraries.sourceRepository.credentialsProperties' : 'testUser:testPassword',
            'testUser' : expectedUser,
            'testPassword' : expectedPassword,
        ])

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == expectedUrl
        resultRef.credentials != null
        resultRef.credentials.username == expectedUser
        resultRef.credentials.password == expectedPassword
    }

    def "configureLibsRepo with Gradle properties takes precedence"() {
        def expectedUrl = 'https://example.com/correct'
        def providers = new FixedTestProviderFactory(
            ['GRADLE_LIBS_REPO_OVERRIDE' : 'https://example.com/wrong'],
            ['org.gradle.libraries.sourceRepository.url' : expectedUrl]
        )

        when:
        DefaultGradleApiSourcesResolver.configureLibsRepo(repository, providers)

        then:
        resultRef.url == expectedUrl
        resultRef.credentials == null
    }

    class StoredRepositoryRef {
        String url
        PasswordCredentials credentials
    }

    class FixedTestProviderFactory extends DefaultProviderFactory {
        private final Map<String, String> environmentVariables
        private final Map<String, String> gradleProperties

        FixedTestProviderFactory(Map<String, String> environmentVariables, Map<String, String> gradleProperties) {
            this.environmentVariables = environmentVariables
            this.gradleProperties = gradleProperties
        }

        @Override
        Provider<String> environmentVariable(String variableName) {
            return Providers.ofNullable(environmentVariables[variableName])
        }

        @Override
        Provider<String> environmentVariable(Provider<String> variableName) {
            return environmentVariable(variableName.get())
        }

        @Override
        Provider<String> gradleProperty(String propertyName) {
            return Providers.ofNullable(gradleProperties[propertyName])
        }

        @Override
        Provider<String> gradleProperty(Provider<String> propertyName) {
            return gradleProperty(propertyName.get())
        }
    }
}
