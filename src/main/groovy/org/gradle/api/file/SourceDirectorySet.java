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

import org.gradle.api.tasks.util.PatternFilterable;

/**
 * A {@code SourceDirectorySet} represents a set of source files composed from a set of source directories.
 */
public interface SourceDirectorySet extends FileTree, PatternFilterable {

    /**
     * Adds the given source directory to this set.
     *
     * @param srcDir The source directory. This is treated as for {@link org.gradle.api.Project#file(Object)}
     * @return this
     */
    SourceDirectorySet srcDir(Object srcDir);

    /**
     * Adds the given source directories to this set.
     *
     * @param srcDirs The source directories. This is treated as for {@link org.gradle.api.Project#files(Object[])}
     * @return this
     */
    SourceDirectorySet srcDirs(Object... srcDirs);
}
