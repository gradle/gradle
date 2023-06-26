/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Incubating;

/**
 * Describes file and directory access permissions for a certain class of users (see {@link FilePermissions}).
 * <p>
 * Permissions consist of:
 * <ul>
 *     <li>READ access: the capability to view the contents of a file, or to list the contents of a directory</li>
 *     <li>WRITE access: the capability to modify or remove the contents of a file, or to add or remove files to/from a directory</li>
 *     <li>EXECUTE access: the capability to run a file as a program; executing a directory doesn't really make sense, it's more like
 *     a traverse permission; for example, a user must have 'execute' access to the 'bin' directory in order to execute the 'ls' or 'cd' commands.</li>
 * </ul>
 *
 * @since 8.3
 */
@Incubating
public interface UserClassFilePermissions {

    /**
     * Describes if a certain class of users has read access to a file or directory.
     * <p>
     * Read access is the capability to view the contents of a file, or to list the contents of a directory.
     */
    boolean getRead();

    /**
     * Describes if a certain class of users has write access to a file or directory.
     * <p>
     * Write access is the capability to modify or remove the contents of a file,
     * or to add or remove files to/from a directory.
     */
    boolean getWrite();

    /**
     * Describes if a certain class of users has execute access to a file or directory.
     * <p>
     * Execute access is the capability to run a file as a program; executing a directory
     * doesn't really make sense, it's more like a traverse permission; for example, a user
     * must have 'execute' access to the 'bin' directory in order to execute the 'ls' or 'cd' commands.
     */
    boolean getExecute();

}
