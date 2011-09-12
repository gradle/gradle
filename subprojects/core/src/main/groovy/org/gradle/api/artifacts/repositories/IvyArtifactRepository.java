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
package org.gradle.api.artifacts.repositories;

import java.net.URI;

/**
 * An artifact repository which uses an Ivy format to store artifacts and meta-data.
 */
public interface IvyArtifactRepository extends ArtifactRepository {
    String DEFAULT_ARTIFACT_PATTERN
            = "[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])";
    String DEFAULT_IVY_PATTERN = DEFAULT_ARTIFACT_PATTERN;

    /**
     * Returns the user name to use when authenticating to this repository.
     *
     * @return The user name. May be null.
     */
    String getUserName();

    /**
     * Sets the user name to use when authenticating to this repository.
     *
     * @param username The user name. May be null.
     */
    void setUserName(String username);

    /**
     * Returns the password to use when authenticating to this repository.
     *
     * @return The password. May be null.
     */
    String getPassword();

    /**
     * Sets the password to use when authenticating to this repository.
     *
     * @param password The password. May be null.
     */
    void setPassword(String password);

    /**
     * The base URL of this repository.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository. The provided value is evaluated as for {@link org.gradle.api.Project#uri(Object)}. This means,
     * for example, you can pass in a File object or a relative path which is evaluated relative to the project directory.
     *
     * Adding a base URL or path is shorthand for the following configuration:
     *    artifactPattern "$base/{@link #DEFAULT_ARTIFACT_PATTERN}"
     *    ivyPattern "$base/{@link #DEFAULT_IVY_PATTERN}"
     *
     * @param url The base URL.
     */
    void setUrl(Object url);

    /**
     * Adds an Ivy artifact pattern to use to locate artifacts in this repository. This pattern will be in addition to any default patterns added via {@link #setUrl}.
     *
     * @param pattern The artifact pattern.
     */
    void artifactPattern(String pattern);

    /**
     * Adds an Ivy pattern to use to locate ivy files in this repository. This pattern will be in addition to any default patterns added via {@link #setUrl}.
     *
     * @param pattern The ivy pattern.
     */
    void ivyPattern(String pattern);
}
