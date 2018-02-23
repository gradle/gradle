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
package org.gradle.api.file;

import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

/**
 * <p>A directory with some associated include and exclude patterns.</p>
 *
 * <p>This interface does not allow mutation. However, the actual implementation may not be immutable.</p>
 */
public interface DirectoryTree {
    /**
     * Returns the base directory of this tree.
     * @return The base dir, never returns null.
     */
    File getDir();

    /**
     * Returns the patterns which select the files under the base directory.
     *
     * @return The patterns, never returns null.
     */
    PatternSet getPatterns();
}
