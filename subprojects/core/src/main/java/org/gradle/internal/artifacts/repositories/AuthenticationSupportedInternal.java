/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.artifacts.repositories;

import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.provider.Property;
import org.gradle.authentication.Authentication;

import java.util.Collection;

public interface AuthenticationSupportedInternal extends AuthenticationSupported {
    /**
     * Returns the configured authentication schemes or an instance of {@link org.gradle.internal.authentication.AllSchemesAuthentication}
     * if none have been configured yet credentials have been configured.
     */
    Collection<Authentication> getConfiguredAuthentication();

    void setConfiguredCredentials(Credentials credentials);

    Property<Credentials> getConfiguredCredentials();
}
