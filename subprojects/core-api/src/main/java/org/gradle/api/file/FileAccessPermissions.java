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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Provides the means of specifying file and directory access permissions.
 * Follows the style of Unix access modes, based on the concept of file ownership.
 * <p>
 * Permissions are grouped into 3 distinct categories:
 * <ul>
 *     <li>OWNER (user) permissions: what actions the owner of the file can perform on the file/directory</li>
 *     <li>GROUP permissions: what actions a user, who is a member of the group that a file belongs to, can perform on the file/directory</li>
 *     <li>OTHER (world) permissions: what actions all other users (non-owner, non-group) can perform on the file/directory</li>
 * </ul>
 * <p>
 * For each category the permissions consist of:
 * <ul>
 *     <li>READ access: grants the capability to view the contents of a file, or to list the contents of a directory
 *     <li>WRITE access: grants the capability to modify or remove the contents of a file, or to add or remove files to/from a directory</li>
 *     <li>EXECUTE access: grant the capability to run a file as a program; executing a directory doesn't really make sense, it's more like
 *     a traverse permission; for example, a user must have 'execute' access to the 'bin' directory in order to execute the 'ls' or 'cd' commands.</li>
 * </ul>
 * <p>
 * The default permissions used differ between files and directories and are as follows:
 * <ul>
 *     <li>FILE: read &amp; write for OWNER, read for GROUP, read for OTHER </li>
 *     <li>DIRECTORY: read, write &amp; execute for OWNER, read &amp; execute for GROUP, read &amp; execute for OTHER</li>
 * </ul>
 *
 * @since 8.1
 */
@Incubating
public interface FileAccessPermissions {

    FileAccessPermission getUser();

    void user(Action<? super FileAccessPermission> configureAction);

    FileAccessPermission getGroup();

    void group(Action<? super FileAccessPermission> configureAction);

    FileAccessPermission getOther();

    void other(Action<? super FileAccessPermission> configureAction);

    void all(Action<? super FileAccessPermission> cofigureAction);

}
