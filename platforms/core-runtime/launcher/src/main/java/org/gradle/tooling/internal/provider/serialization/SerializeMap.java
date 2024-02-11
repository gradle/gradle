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

package org.gradle.tooling.internal.provider.serialization;

import java.util.Map;

public interface SerializeMap {
    /**
     * Visits a class to be serialized, returning the id of the deserialize ClassLoader to associate this class with.
     * The id is unique only for this serialization, and is used as the key for the map built by {@link #collectClassLoaderDefinitions(Map)}.
     *
     * @return The ClassLoader id.
     */
    short visitClass(Class<?> target);

    /**
     * Collects the set of ClassLoader definitions to use to deserialize the graph.
     *
     * @param details The map from ClassLoader id to details to use create that ClassLoader.
     */
    void collectClassLoaderDefinitions(Map<Short, ClassLoaderDetails> details);
}
