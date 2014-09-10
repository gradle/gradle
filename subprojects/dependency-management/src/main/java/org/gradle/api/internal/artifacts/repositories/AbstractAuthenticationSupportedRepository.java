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
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.ClosureBackedAction;

public abstract class AbstractAuthenticationSupportedRepository extends AbstractArtifactRepository implements AuthenticationSupported {
    private final PasswordCredentials passwordCredentials;

    AbstractAuthenticationSupportedRepository(PasswordCredentials credentials) {
        this.passwordCredentials = credentials;
    }

    public PasswordCredentials getCredentials() {
        return passwordCredentials;
    }

    public void credentials(Closure closure) {
        credentials(new ClosureBackedAction<PasswordCredentials>(closure));
    }

    public void credentials(Action<? super PasswordCredentials> action) {
        action.execute(passwordCredentials);
    }
}
