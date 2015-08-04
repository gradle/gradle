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

package org.gradle.api.internal.authentication;

import com.google.common.collect.Sets;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.util.Set;

public abstract class AbstractAuthentication implements AuthenticationInternal {
    private Credentials credentials;
    private String name;
    private Set<Class<? extends Credentials>> supportedCredentials;

    public AbstractAuthentication(String name) {
        this.name = name;
        this.supportedCredentials = Sets.newHashSet();
    }

    public AbstractAuthentication(String name, Class<? extends Credentials>... supportedCredentials) {
        this.name = name;
        this.supportedCredentials = Sets.newHashSet(supportedCredentials);
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
    public Set<Class<? extends Credentials>> getSupportedCredentials() {
        return supportedCredentials;
    }

    @Override
    public <T extends Credentials> boolean supports(final T credentials) {
        return CollectionUtils.any(getSupportedCredentials(), new Spec<Class<? extends Credentials>>() {
            @Override
            public boolean isSatisfiedBy(Class<? extends Credentials> element) {
                return element.isAssignableFrom(credentials.getClass());
            }
        });
    }
}
