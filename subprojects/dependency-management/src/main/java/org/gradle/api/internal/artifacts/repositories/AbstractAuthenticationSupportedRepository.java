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
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.api.credentials.Credentials;
import org.gradle.internal.Transformers;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;
import org.gradle.internal.credentials.DefaultAwsCredentials;
import org.gradle.internal.reflect.Instantiator;

public abstract class AbstractAuthenticationSupportedRepository extends AbstractArtifactRepository implements AuthenticationSupportedInternal {
    private Credentials credentials;
    private final Instantiator instantiator;

    AbstractAuthenticationSupportedRepository(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public PasswordCredentials getCredentials() {
        return getCredentials(PasswordCredentials.class);
    }

    @Override
    public <T extends Credentials> T getCredentials(Class<T> clazz) {
        if (credentials == null) {
            credentials = newCredentials(clazz);
        }
        return Transformers.cast(clazz).transform(credentials);
    }

    public void credentials(Action<? super PasswordCredentials> action) {
        credentials(PasswordCredentials.class, action);
    }

    public <T extends Credentials> void credentials(Class<T> clazz, Action<? super T> action) throws IllegalStateException {
        action.execute(getCredentials(clazz));
    }

    private <T extends Credentials> T newCredentials(Class<T> clazz) {
        if (clazz == AwsCredentials.class) {
            return Transformers.cast(clazz).transform(instantiator.newInstance(DefaultAwsCredentials.class));
        } else if (clazz == PasswordCredentials.class) {
            return Transformers.cast(clazz).transform(instantiator.newInstance(DefaultPasswordCredentials.class));
        } else {
            throw new IllegalArgumentException(String.format("Unknown credentials type: '%s'.", clazz.getName()));
        }
    }

    public Credentials getConfiguredCredentials() {
        return credentials;
    }
}
