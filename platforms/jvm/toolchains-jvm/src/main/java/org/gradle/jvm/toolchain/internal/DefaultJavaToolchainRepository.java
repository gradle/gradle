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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.jvm.toolchain.JavaToolchainResolver;

import javax.inject.Inject;
import java.util.Collection;

public abstract class DefaultJavaToolchainRepository implements JavaToolchainRepositoryInternal {

    private final String name;

    private final AuthenticationContainer authenticationContainer;

    private final AuthenticationSupporter authenticationSupporter;

    private final ProviderFactory providerFactory;

    @Inject
    public DefaultJavaToolchainRepository(
            String name,
            AuthenticationContainer authenticationContainer,
            AuthenticationSupporter authenticationSupporter,
            ProviderFactory providerFactory
    ) {
        this.name = name;
        this.authenticationContainer = authenticationContainer;
        this.authenticationSupporter = authenticationSupporter;
        this.providerFactory = providerFactory;
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract Property<Class<? extends JavaToolchainResolver>> getResolverClass();

    @Override
    public Collection<Authentication> getConfiguredAuthentication() {
        return authenticationSupporter.getConfiguredAuthentication();
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
        authenticationSupporter.credentials(credentialsType, providerFactory.provider(() -> name));
    }

    @Override
    public void authentication(Action<? super AuthenticationContainer> action) {
        authenticationSupporter.authentication(action);
    }

    @Override
    public AuthenticationContainer getAuthentication() {
        return authenticationContainer;
    }
}
