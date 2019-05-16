/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.repositories;

import java.net.URI;

/**
 * A repository that supports resolving artifacts from a URL.
 */
public interface UrlArtifactRepository {

    /**
     * The base URL of this repository.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository.
     *
     * @param url The base URL.
     * @since 4.0
     */
    void setUrl(URI url);

    /**
     * Sets the base URL of this repository.
     *
     * @param url The base URL.
     * @since 4.0
     */
    void setUrl(Object url);

    /**
     * Specifies whether it is acceptable to communicate with a repository over an insecure HTTP connection.
     *
     * @since 5.5
     */
    void allowInsecureProtocol(boolean allowInsecureProtocol);

    /**
     * Specifies whether it is acceptable to communicate with a repository over an insecure HTTP connection.
     *
     * @since 5.5
     */
    boolean isAllowInsecureProtocol();
}
