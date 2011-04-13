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
package org.gradle.api.artifacts.dsl;

/**
 * An artifact repository which uses an Ivy format to store artifacts and meta-data.
 */
public interface IvyArtifactRepository extends ArtifactRepository {
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
     * Adds an Ivy artifact pattern to use to locate artifacts in this repository.
     *
     * @param pattern The artifact pattern.
     */
    void artifactPattern(String pattern);
}
