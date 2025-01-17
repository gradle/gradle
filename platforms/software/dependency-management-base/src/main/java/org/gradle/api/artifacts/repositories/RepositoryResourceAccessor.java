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
package org.gradle.api.artifacts.repositories;

import org.gradle.api.Action;

import java.io.InputStream;

/**
 * Provides access to resources on an artifact repository. Gradle takes care of caching
 * the resources locally. The scope of the cache may depend on the accessor: users should
 * refer to the javadocs of the methods providing an accessor to determine the scope.
 *
 * @since 4.0
 */
public interface RepositoryResourceAccessor {
    /**
     * Perform an action on the contents of a remote resource.
     * @param relativePath path to the resource, relative to the base URI of the repository
     * @param action action to execute on the resource
     */
    void withResource(String relativePath, Action<? super InputStream> action);
}
