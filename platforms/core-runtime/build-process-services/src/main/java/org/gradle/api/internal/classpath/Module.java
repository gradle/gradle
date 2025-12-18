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
package org.gradle.api.internal.classpath;

import org.gradle.internal.classpath.ClassPath;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Metadata about a module of the Gradle distribution.
 */
@NullMarked
public interface Module {

    /**
     * Get the name of this module.
     */
    String getName();

    /**
     * Gets the names of all modules that this module depends on.
     */
    List<String> getDependencyNames();

    /**
     * Returns the classpath for the module implementation. This is the classpath of the module itself. Does not include any dependencies.
     */
    ClassPath getImplementationClasspath();

    /**
     * An optional set of coordinates, which if present, allows this module to be loaded by
     * dependency resolution directly from the distribution.
     */
    @Nullable ModuleAlias getAlias();

    /**
     * The module identity that this distribution module implements
     */
    interface ModuleAlias {

        String getGroup();

        String getName();

        String getVersion();

    }

}
