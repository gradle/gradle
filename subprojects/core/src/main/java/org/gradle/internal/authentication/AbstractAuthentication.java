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

package org.gradle.internal.authentication;

import org.gradle.api.credentials.Credentials;
import org.gradle.authentication.Authentication;

public abstract class AbstractAuthentication implements AuthenticationInternal {
    private final String name;
    private final Class<? extends Credentials> supportedCredentialType;
    private final Class<? extends Authentication> type;
    private Credentials credentials;

    public AbstractAuthentication(String name, Class<? extends Authentication> type) {
        this.name = name;
        this.supportedCredentialType = null;
        this.type = type;
    }

    public AbstractAuthentication(String name, Class<? extends Authentication> type, Class<? extends Credentials> supportedCredential) {
        this.name = name;
        this.supportedCredentialType = supportedCredential;
        this.type = type;
    }

    @Override
    public Credentials getCredentials() {
        return credentials;
    }

    @Override
    public void setCredentials(Credentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean supports(final Credentials credentials) {
        return supportedCredentialType.isAssignableFrom(credentials.getClass());
    }

    @Override
    public Class<? extends Authentication> getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("'%s'(%s)", getName(), getType().getSimpleName());
    }
}
