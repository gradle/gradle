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
 * Describes file and directory access permissions for all classes of system users.
 * <p>
 * Follows the style of Unix file permissions, based on the concept of file ownership.
 * <p>
 * Permissions are grouped into 3 distinct categories (representing different classes of users):
 * <ul>
 *     <li>OWNER (user) permissions: what actions the owner of the file/directory can perform on the file/directory</li>
 *     <li>GROUP permissions: what actions a user, who is a member of the group that a file/directory belongs to, can perform on the file/directory</li>
 *     <li>OTHER (world) permissions: what actions all other users (non-owner, non-group) can perform on the file/directory</li>
 * </ul>
 * <p>
 * For further details on specific permission for a certain class of user see {@link UserClassFilePermissions}, but in essence
 * each class of users can have the right to READ, WRITE or EXECUTE files.
 * <p>
 * The default permissions used differ between files and directories and are as follows:
 * <ul>
 *     <li>FILE: read &amp; write for OWNER, read for GROUP, read for OTHER (0644, rw-r--r--)</li>
 *     <li>DIRECTORY: read, write &amp; execute for OWNER, read &amp; execute for GROUP, read &amp; execute for OTHER (0755, rwxr-xr-x)</li>
 * </ul>
 *
 * @since 8.3
 */
@Incubating
public interface FilePermissions {

    /**
     * Describes what actions the owner of the file can perform on the file/directory.
     * <p>
     * For further details about possible actions see {@link UserClassFilePermissions}.
     */
    UserClassFilePermissions getUser();

    /**
     * Describes what actions a user, who is a member of the group that the file/directory belongs to,
     * can perform on the file/directory.
     * <p>
     * For further details about possible actions see {@link UserClassFilePermissions}.
     */
    UserClassFilePermissions getGroup();

    /**
     * Describes what actions all other users (non-owner, non-group) can perform on the file/directory.
     * <p>
     * For further details about possible actions see {@link UserClassFilePermissions}.
     */
    UserClassFilePermissions getOther();

    /**
     * Converts the permissions for the various user groups to a numeric Unix permission.
     * See {@link ConfigurableFilePermissions#unix(String)} for details.
     */
    int toUnixNumeric();

}
