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
 * Metadata about a module of a Gradle distribution.
 */
@NullMarked
public interface Module {

    /**
     * Get the name of this module.
     */
    String getName();

    /**
     * Returns the classpath for the module implementation. This is the classpath of the module itself. Does not include any dependencies.
     */
    ClassPath getImplementationClasspath();

    /**
     * Gets the names of all modules that this module depends on.
     */
    List<String> getDependencyNames();

    /**
     * An optional set of Maven module coordinates that identifies this module outside
     * a Gradle distribution. These are the coordinates that would traditionally be used
     * to resolve this module from a repository if this module were to live in an external
     * repository.
     */
    @Nullable ModuleAlias getAlias();

    /**
     * The Maven module identity that this distribution module implements.
     */
    interface ModuleAlias {

        String getGroup();

        String getName();

        String getVersion();

    }

}
