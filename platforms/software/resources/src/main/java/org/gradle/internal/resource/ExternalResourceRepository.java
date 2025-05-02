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

package org.gradle.internal.resource;

/**
 * Provides access to {@link ExternalResource} implementations, given a URI or resource name.
 */
public interface ExternalResourceRepository {
    /**
     * Returns a copy of this repository with progress logging enabled.
     */
    ExternalResourceRepository withProgressLogging();

    /**
     * Returns the resource with the given name. Note that this method does not access the resource in any way, it simply creates an object that can. To access the resource, use the methods on the returned object.
     *
     * @param resource The location of the resource
     * @param revalidate Ensure the external resource is not stale when reading its content
     */
    ExternalResource resource(ExternalResourceName resource, boolean revalidate);

    /**
     * Returns the resource with the given name. Note that this method does not access the resource in any way, it simply creates an object that can. To access the resource, use the methods on the returned object.
     *
     * @param resource The location of the resource
     */
    ExternalResource resource(ExternalResourceName resource);
}
