/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.http;

import org.gradle.api.credentials.PasswordCredentials;

import javax.annotation.Nullable;

/**
 * Password credentials for a HTTP build cache backend.
 *
 * @since 3.5
 */
public class HttpBuildCacheCredentials implements PasswordCredentials {
    private String username;
    private String password;

    /**
     * Returns the user name to use when authenticating to the HTTP build cache.
     *
     * @return The user name. May be null.
     */
    @Override
    @Nullable
    public String getUsername() {
        return username;
    }

    /**
     * Sets the user name to use when authenticating to the HTTP build cache.
     *
     * @param username The user name. May be null.
     */
    @Override
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /**
     * Returns the password to use when authenticating to the HTTP build cache.
     *
     * @return The password. May be null.
     */
    @Override
    @Nullable
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password to use when authenticating to the HTTP build cache.
     *
     * @param password The password. May be null.
     */
    @Override
    public void setPassword(@Nullable String password) {
        this.password = password;
    }
}
