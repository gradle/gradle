/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.authentication.Authentication;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.util.Collection;

public abstract class AbstractAuthenticationSupportedRepository extends AbstractArtifactRepository implements AuthenticationSupportedInternal {
    private final AuthenticationSupporter delegate;

    AbstractAuthenticationSupportedRepository(Instantiator instantiator, AuthenticationContainer authenticationContainer) {
        this.delegate = new AuthenticationSupporter(instantiator, authenticationContainer);
    }

    @Override
    public PasswordCredentials getCredentials() {
        return delegate.getCredentials();
    }

    @Override
    public <T extends Credentials> T getCredentials(Class<T> credentialsType) {
        return delegate.getCredentials(credentialsType);
    }

    @Nullable
    @Override
    public Credentials getConfiguredCredentials() {
        return delegate.getConfiguredCredentials();
    }

    @Override
    public void setConfiguredCredentials(Credentials credentials) {
        delegate.setConfiguredCredentials(credentials);
    }

    @Override
    public void credentials(Action<? super PasswordCredentials> action) {
        delegate.credentials(action);
    }

    @Override
    public <T extends Credentials> void credentials(Class<T> credentialsType, Action<? super T> action) throws IllegalStateException {
        delegate.credentials(credentialsType, action);
    }

    @Override
    public void authentication(Action<? super AuthenticationContainer> action) {
        delegate.authentication(action);
    }

    @Override
    public AuthenticationContainer getAuthentication() {
        return delegate.getAuthentication();
    }

    @Override
    public Collection<Authentication> getConfiguredAuthentication() {
        return delegate.getConfiguredAuthentication();
    }
}
