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
package org.gradle.plugins.cpp.model;

import org.gradle.api.Named;
import org.gradle.api.file.SourceDirectorySet;
import groovy.lang.Closure;

/**
 * A {@code CppSourceSet} represents a logical group of C++ source.
 */
public interface CppSourceSet extends Named {

    /**
     * Returns the name of this source set.
     *
     * @return The name. Never returns null.
     */
    String getName();

    /**
     * Returns the C++ source which is to be compiled by the C++ compiler.
     *
     * @return the C++ source. Never returns null.
     */
    SourceDirectorySet getCpp();

    /**
     * Configures the C++ source for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the C++ source.
     *
     * @param configureClosure The closure to use to configure the C++ source.
     * @return this
     */
    CppSourceSet cpp(Closure configureClosure);

    /**
     * Returns the local header files associated with this source.
     *
     * @return the header files. Never returns null.
     */
    SourceDirectorySet getHeaders();

    /**
     * Configures the headers for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the header files.
     *
     * @param configureClosure The closure to use to configure the header files source.
     * @return this
     */
    CppSourceSet headers(Closure configureClosure);

}