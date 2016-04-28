/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.plugins.repositories;

import org.gradle.api.Incubating;

import java.net.URI;

/**
 * Represents an Ivy repository which contains Gradle plugins.
 */
@Incubating
public interface IvyPluginRepository extends PluginRepository {
    /**
     * The base URL of this repository. This URL is used to find artifact files.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository. This URL is used to find artifact files.
     *
     * <p>The provided value is evaluated as per {@link org.gradle.api.Project#uri(Object)}. This
     * means, for example, you can pass in a {@code File} object, or a relative path to be
     * evaluated relative to the directory of the {@code settings.gradle} file in which this
     * repository is declared.
     *
     * @param url The base URL.
     */
    void setUrl(Object url);
}
