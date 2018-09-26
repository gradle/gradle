/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;

import javax.annotation.Nullable;

/**
 * A {@code GroovySourceSetConvention} defines the properties and methods added to a {@link SourceSet} by the {@link
 * org.gradle.api.plugins.GroovyPlugin}.
 */
public interface GroovySourceSet {
    /**
     * Returns the source to be compiled by the Groovy compiler for this source set. Any Java source present in this set
     * will be passed to the Groovy compiler for joint compilation.
     *
     * @return The Groovy/Java source. Never returns null.
     */
    SourceDirectorySet getGroovy();

    /**
     * Configures the Groovy source for this set.
     *
     * <p>The given closure is used to configure the {@link SourceDirectorySet} which contains the Groovy source.
     *
     * @param configureClosure The closure to use to configure the Groovy source.
     * @return this
     */
    GroovySourceSet groovy(@Nullable Closure configureClosure);

    /**
     * Configures the Groovy source for this set.
     *
     * <p>The given action is used to configure the {@link SourceDirectorySet} which contains the Groovy source.
     *
     * @param configureAction The action to use to configure the Groovy source.
     * @return this
     */
    GroovySourceSet groovy(Action<? super SourceDirectorySet> configureAction);

    /**
     * All Groovy source for this source set.
     *
     * @return the Groovy source. Never returns null.
     */
    SourceDirectorySet getAllGroovy();
}
