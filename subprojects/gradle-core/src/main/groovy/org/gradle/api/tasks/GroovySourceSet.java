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

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.file.FileTree;

/**
 * A {@code GroovySourceSetConvention} defines the properties and methods added to a {@link
 * org.gradle.api.tasks.SourceSet} by the {@link org.gradle.api.plugins.GroovyPlugin}.
 */
public interface GroovySourceSet {
    /**
     * Returns all Groovy/Java source to be compiled by the Groovy compiler for this project.
     *
     * @return The Groovy/Java source. Never returns null.
     */
    SourceDirectorySet getGroovy();

    /**
     * All Groovy source for this project.
     *
     * @return the Groovy source. Never returns null.
     */
    FileTree getAllGroovy();
}
