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

package org.gradle.api.credentials;

import javax.annotation.Nullable;

/**
 * A username/password credentials that can be used to login to something protected by a username and password.
 *
 * @since 3.5
 */
public interface PasswordCredentials extends Credentials {
    /**
     * Returns the user name to use when authenticating.
     *
     * @return The user name. May be null.
     */
    @Nullable
    String getUsername();

    /**
     * Sets the user name to use when authenticating.
     *
     * @param userName The user name. May be null.
     */
    void setUsername(@Nullable String userName);

    /**
     * Returns the password to use when authenticating.
     *
     * @return The password. May be null.
     */
    @Nullable
    String getPassword();

    /**
     * Sets the password to use when authenticating.
     *
     * @param password The password. May be null.
     */
    void setPassword(@Nullable String password);
}
