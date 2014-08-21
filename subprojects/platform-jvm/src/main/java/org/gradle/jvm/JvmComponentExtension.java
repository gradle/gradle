/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectContainer;

/**
 * The configuration for jvm components created by this build.
 */
@Incubating
public interface JvmComponentExtension {
    /**
     * Provides the set of JVM libraries that can be created by this build.
     */
    NamedDomainObjectContainer<JvmLibrarySpec> getLibraries();

    /**
     * Configures the set of JVM libraries.
     */
    void libraries(Action<? super NamedDomainObjectContainer<? super JvmLibrarySpec>> action);
}
