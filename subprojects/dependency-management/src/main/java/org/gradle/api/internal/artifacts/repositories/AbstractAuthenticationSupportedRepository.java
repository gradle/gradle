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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.artifacts.repositories.AwsCredentials;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.credentials.Credentials;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.reflect.Instantiator;

public abstract class AbstractAuthenticationSupportedRepository extends AbstractArtifactRepository implements AuthenticationSupported {
    private Credentials credentials;
    private final Instantiator instantiator;

    AbstractAuthenticationSupportedRepository(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public PasswordCredentials getCredentials() {
        if(credentials != null && !(credentials instanceof PasswordCredentials)) {
            throw new IllegalStateException(String.format("Requested credentials must be of type '%s'.", PasswordCredentials.class.getName()));
        }
        return (PasswordCredentials) credentials;
    }

    public void credentials(Closure closure) {
        credentials(new ClosureBackedAction<PasswordCredentials>(closure));
    }

    public void credentials(Action<? super PasswordCredentials> action) {
        if (credentials != null) {
            throw new IllegalStateException("Cannot overwrite already configured credentials.");
        }
        credentials = instantiator.newInstance(DefaultPasswordCredentials.class);
        action.execute((PasswordCredentials)credentials);
    }

    public <T extends Credentials> void credentials(Class<T> clazz, Action<? super T> action) throws IllegalStateException {
        if(credentials != null) {
            throw new IllegalStateException("Cannot overwrite already configured credentials.");
        }
        if (clazz == AwsCredentials.class) {
            credentials = instantiator.newInstance(DefaultAwsCredentials.class);
        } else if (clazz == PasswordCredentials.class) {
            credentials= instantiator.newInstance(DefaultPasswordCredentials.class);
        }
        action.execute((T) credentials);
    }

    public Credentials getAlternativeCredentials() {
        return credentials;
    }
}
