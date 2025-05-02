/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.HasInternalProtocol;

/**
 * A specification for deleting files from the filesystem.
 */
@HasInternalProtocol
public interface DeleteSpec {
    /**
     * Specifies the files to delete.
     *
     * @param files the list of files which should be deleted. Any type of object
     * accepted by {@link org.gradle.api.Project#files(Object...)}
     */
    DeleteSpec delete(Object... files);

    /**
     * Specifies whether or not symbolic links should be followed during deletion.
     *
     * @param followSymlinks deletion will follow symlinks when true.
     */
    void setFollowSymlinks(boolean followSymlinks);
}
