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
 * Provides the means of specifying file and directory access permissions for a certain class of users (see {@link ConfigurableFilePermissions}).
 * <p>
 * For details on the actual permissions see {@link UserClassFilePermissions}.
 *
 * @since 8.3
 */
@Incubating
public interface ConfigurableUserClassFilePermissions extends UserClassFilePermissions {

    /**
     * Enables or disables read access to a file or directory for a certain class of users.
     * <p>
     * Read access grants the capability to view the contents of a file,
     * or to list the contents of a directory.
     */
    void setRead(boolean read);

    /**
     * Enables or disables write access to a file or directory for a certain class of users.
     * <p>
     * Write access grants the capability to modify or remove the contents of a file,
     * or to add or remove files to/from a directory.
     */
    void setWrite(boolean write);

    /**
     * Enables or disables execute access to a file or directory for a certain class of users.
     * <p>
     * Execute access grant the capability to run a file as a program; executing a directory
     * doesn't really make sense, it's more like a traverse permission; for example, a user
     * must have 'execute' access to the 'bin' directory in order to execute the 'ls' or 'cd' commands.
     */
    void setExecute(boolean execute);

}
