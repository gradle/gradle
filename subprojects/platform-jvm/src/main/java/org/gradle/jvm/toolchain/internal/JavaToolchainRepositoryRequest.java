/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.toolchain.management.JavaToolchainRepositoryRegistration;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationInternal;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRequestConfiguration;

import javax.inject.Inject;
import java.net.URI;
import java.util.Collection;

public class JavaToolchainRepositoryRequest implements JavaToolchainRepositoryRequestConfiguration {

    private final JavaToolchainRepositoryRegistrationInternal registration;

    private final AuthenticationSupporter authenticationSupporter;

    private final ProviderFactory providerFactory;

    @Inject
    public JavaToolchainRepositoryRequest(JavaToolchainRepositoryRegistrationInternal registration, AuthenticationSupporter authenticationSupporter, ProviderFactory providerFactory) {
        this.registration = registration;
        this.authenticationSupporter = authenticationSupporter;
        this.providerFactory = providerFactory;
    }

    public JavaToolchainRepositoryRegistration getRegistration() {
        return registration;
    }

    public JavaToolchainRepository getRepository() {
        return registration.getProvider().get();
    }

    public Collection<Authentication> getAuthentications(URI uri) {
        Collection<Authentication> configuredAuthentication = authenticationSupporter.getConfiguredAuthentication();

        for (Authentication authentication : configuredAuthentication) {
            AuthenticationInternal authenticationInternal = (AuthenticationInternal) authentication;
            if (uri.getScheme().startsWith("http")) {
                authenticationInternal.addHost(uri.getHost(), uri.getPort());
            }
        }
        return configuredAuthentication;
    }

    @Override
    public PasswordCredentials getCredentials() {
        return authenticationSupporter.getCredentials();
    }

    @Override
    public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
        return authenticationSupporter.getCredentials(credentialsType);
    }

    @Override
    public void credentials(Action<? super PasswordCredentials> action) {
        authenticationSupporter.credentials(action);
    }

    @Override
    public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) {
        authenticationSupporter.credentials(credentialsType, action);
    }

    @Override
    public void credentials(Class<? extends Credentials> credentialsType) {
        authenticationSupporter.credentials(credentialsType, providerFactory.provider(() -> getRegistration().getName()));
    }

    @Override
    public void authentication(Action<? super AuthenticationContainer> action) {
        authenticationSupporter.authentication(action);
    }
}
