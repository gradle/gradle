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

import org.gradle.api.tasks.bundling.Decompressor;

/**
 * File tree with decompressing capabilities
 */
public interface ArchiveFileTree extends FileTree {

    /**
     * Decompressor for this archive file tree
     *
     * @return decompressor
     */
    Decompressor getDecompressor();

    /**
     * Configures decompressor for this archive file tree
     *
     * @param decompressor to set
     */
    void setDecompressor(Decompressor decompressor);
}