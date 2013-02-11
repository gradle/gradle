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

package org.gradle.api.resources;

/**
 * Provides access to resource-specific utility methods, for example factory methods that create various resources.
 */
public interface ResourceHandler {

    /**
     * Creates resource that points to a gzip compressed file at the given path.
     * The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    ReadableResource gzip(Object path);

    /**
     * Creates resource that points to a bzip2 compressed file at the given path.
     * The path is evaluated as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param path The path evaluated as per {@link org.gradle.api.Project#file(Object)}.
     */
    ReadableResource bzip2(Object path);
}
