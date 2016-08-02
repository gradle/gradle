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
package org.gradle.api.file;

import groovy.lang.Closure;
import org.gradle.api.Action;

/**
 * Specifies sources for a file copy.
 */
public interface CopySourceSpec {
    /**
     * Specifies source files or directories for a copy. The given paths are evaluated as per {@link
     * org.gradle.api.Project#files(Object...)}.
     *
     * @param sourcePaths Paths to source files for the copy
     */
    CopySourceSpec from(Object... sourcePaths);

    /**
     * Specifies the source files or directories for a copy and creates a child {@code CopySourceSpec}. The given source
     * path is evaluated as per {@link org.gradle.api.Project#files(Object...)} .
     *
     * @param sourcePath Path to source for the copy
     * @param configureClosure closure for configuring the child CopySourceSpec
     */
    CopySourceSpec from(Object sourcePath, Closure configureClosure);

    /**
     * Specifies the source files or directories for a copy and creates a child {@code CopySpec}. The given source
     * path is evaluated as per {@link org.gradle.api.Project#files(Object...)} .
     *
     * @param sourcePath Path to source for the copy
     * @param configureAction action for configuring the child CopySpec
     */
    CopySourceSpec from(Object sourcePath, Action<? super CopySpec> configureAction);
}
