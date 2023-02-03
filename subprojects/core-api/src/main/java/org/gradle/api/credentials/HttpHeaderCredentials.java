/*
 * Copyright 2018 the original author or authors.
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
 * Credentials that can be used to login to a protected server, e.g. a remote repository by using HTTP header.
 *
 * The properties used for creating credentials from a property are {@code repoAuthHeaderName} and {@code repoAuthHeaderValue}, where {@code repo} is the identity of the repository.
 *
 * @since 4.10
 */
public interface HttpHeaderCredentials extends Credentials {

    /**
     * Returns the header name to use when authenticating.
     *
     * @return The header name. May be null.
     */
    @Nullable
    String getName();

    /**
     * Sets the header name to use when authenticating.
     *
     * @param name The header name. May be null.
     */
    void setName(@Nullable String name);

    /**
     * Returns the header value to use when authenticating.
     *
     * @return The header value. May be null.
     */
    @Nullable
    String getValue();

    /**
     * Sets the header value to use when authenticating.
     *
     * @param value The header value. May be null.
     */
    void setValue(@Nullable String value);

}
