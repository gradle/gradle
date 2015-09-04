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
import java.util.Set;

/**
 * An artifact repository which uses a Maven format to store artifacts and meta-data.
 * <p>
 * Repositories of this type are created by the {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(org.gradle.api.Action)} group of methods.
 */
public interface MavenArtifactRepository extends ArtifactRepository, AuthenticationSupported {

    /**
     * The base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #setArtifactUrls(Iterable)}.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository. This URL is used to find both POMs and artifact files. You can add additional URLs to use to look for artifact files, such as jars, using {@link
     * #setArtifactUrls(Iterable)}.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated relative
     * to the project directory.
     *
     * @param url The base URL.
     */
    void setUrl(Object url);

    /**
     * Returns the additional URLs to use to find artifact files. Note that these URLs are not used to find POM files.
     *
     * @return The additional URLs. Returns an empty list if there are no such URLs.
     */
    Set<URI> getArtifactUrls();

    /**
     * Adds some additional URLs to use to find artifact files. Note that these URLs are not used to find POM files.
     *
     * <p>The provided values are evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated
     * relative to the project directory.
     *
     * @param urls The URLs to add.
     */
    void artifactUrls(Object... urls);

    /**
     * Sets the additional URLs to use to find artifact files. Note that these URLs are not used to find POM files.
     *
     * <p>The provided values are evaluated as per {@link org.gradle.api.Project#uri(Object)}. This means, for example, you can pass in a {@code File} object, or a relative path to be evaluated
     * relative to the project directory.
     *
     * @param urls The URLs.
     */
    void setArtifactUrls(Iterable<?> urls);
}
