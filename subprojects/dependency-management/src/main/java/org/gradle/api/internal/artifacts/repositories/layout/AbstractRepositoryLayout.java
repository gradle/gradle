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
package org.gradle.api.internal.artifacts.repositories.layout;

import org.gradle.api.artifacts.repositories.RepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.resolver.PatternBasedResolver;

import java.net.URI;
import java.util.Set;

/**
 * Represents the directory structure for a repository.
 */
public abstract class AbstractRepositoryLayout implements RepositoryLayout {
    /**
     * Given the base URI, apply the patterns and other configuration for this layout to the supplied resolver.
     *
     * @param baseUri The base URI for the repository.
     * @param resolver The ivy resolver that will be used to resolve this layout.
     */
    public abstract void apply(URI baseUri, PatternBasedResolver resolver);

    /**
     * Add any schemes registered as patterns in this layout, given the supplied base URI.
     * These are used to determine which repository implementation can be used (local file, http, etc).
     *
     * @param baseUri The baseUri of the repository.
     * @param schemes The set of schemes to add to.
     */
    public void addSchemes(URI baseUri, Set<String> schemes) {
        if (baseUri != null) {
            schemes.add(baseUri.getScheme());
        }
    }
}
