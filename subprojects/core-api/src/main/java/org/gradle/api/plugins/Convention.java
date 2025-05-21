/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.api.plugins;

import org.jspecify.annotations.Nullable;

/**
 * <p>A {@code Convention} manages a set of <i>convention objects</i>. When you add a convention object to a {@code
 * Convention}, and the properties and methods of the convention object become available as properties and methods of
 * the object which the convention is associated to. A convention object is simply a POJO or POGO. Usually, a {@code
 * Convention} is used by plugins to extend a {@link org.gradle.api.Project} or a {@link org.gradle.api.Task}.</p>
 *
 * DO NOT USE THIS INTERFACE.
 *
 * @deprecated This interface should not be used and only preserved to maintain compatibility compiled against older Gradle versions. This is scheduled for removal in Gradle 9.
 * @see org.gradle.api.plugins.ExtensionAware
 */
@Deprecated
public interface Convention extends ExtensionContainer {

    /**
     * Locates the plugin convention object with the given type.
     *
     * DO NOT USE THIS METHOD.
     *
     * @param type The convention object type.
     * @return always null, only here for compatibility with Gradle plugins compiled against Gradle &lt; 8.2
     * @throws IllegalStateException When there are multiple matching objects.
     * @deprecated This is just here for now to maintain compatibility with Gradle plugins compiled against Gradle &lt; 8.2. This is scheduled for removal in Gradle 9.
     * @see org.gradle.api.plugins.ExtensionAware
     */
    @Nullable
    @Deprecated
    default <T> T findPlugin(Class<T> type) throws IllegalStateException{
        return null;
    }
}
