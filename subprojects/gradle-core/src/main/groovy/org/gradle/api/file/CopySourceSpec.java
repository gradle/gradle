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

/**
 * Specifies sources for a file copy
 * @author Steve Appling
 */
public interface CopySourceSpec {
    /**
     * Specifies source files or directories for a copy.
     * The toString() method of each sourcePath is used to get a path.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * Relative paths will be evaluated relative to the project directory.
     * @param sourcePaths Paths to source directories for the copy
     */
    CopySourceSpec from(Object... sourcePaths);

    /**
     * Specifies the source for a copy and creates a child CopySourceSpec.
     * SourcePath.toString is used as the path.
     * The source is set on the child CopySourceSpec, not on this one.
     * This may be a path to a single file to copy or to a directory.  If the path is to a directory,
     * then the contents of the directory will be copied.
     * The paths are evaluated like {@link org.gradle.api.Project#file(Object) Project.file() }.
     * @param sourcePath Path to source for the copy
     * @param c closure for configuring the child CopySourceSpec
     */
    CopySourceSpec from(Object sourcePath, Closure c);
}
