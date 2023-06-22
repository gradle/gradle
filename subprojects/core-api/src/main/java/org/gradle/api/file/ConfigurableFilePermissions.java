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
 * Provides the means of specifying file and directory access permissions for all classes of system users.
 * <p>
 * For details on classes of users and file/directory permissions see {@link FilePermissions}.
§§ * <p>
 * An example usage of this functionality would be configuring a copy task and explicitly specifying the destination file permissions:
 * <pre>
 * from(...)
 * into(...)
 * filePermissions {
 *     user {
 *         read = true
 *         execute = true
 *     }
 *     other.execute = false
 * }
 * </pre>
 * @since 8.3
 */
@Incubating
public interface ConfigurableFilePermissions extends FilePermissions {

    /**
     * Returns the permissions the owner of the file has for the file/directory.
     * <p>
     * The returned object is live, modifying it will change the user's permissions.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     */
    @Override
    ConfigurableUserClassFilePermissions getUser();

    /**
     * Modifies the permissions the owner of the file has for the file/directory.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     * <p>
     * Note that the provided configuration action only applies incremental modifications on top of whatever permission
     * the user has at the moment and that the default values permissions start out are different for files and directories
     * (see {@link UserClassFilePermissions}).
     */
    void user(Action<? super ConfigurableUserClassFilePermissions> configureAction);

    /**
     * Returns the permissions a user, who is a member of the group that the file/directory belongs to, has for the file/directory.
     * <p>
     * The returned object is live, modifying it will change the user's permissions.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     */
    @Override
    ConfigurableUserClassFilePermissions getGroup();

    /**
     * Modifies the permissions a user, who is a member of the group that the file/directory belongs to, has for the file/directory.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     * <p>
     * Note that the provided configuration action only applies incremental modifications on top of whatever permission
     * the user has at the moment and that the default values permissions start out are different for files and directories
     * (see {@link UserClassFilePermissions}).
     */
    void group(Action<? super ConfigurableUserClassFilePermissions> configureAction);

    /**
     * Returns the permissions all other users (non-owner, non-group) have for the file/directory.
     * <p>
     * The returned object is live, modifying it will change the user's permissions.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     */
    @Override
    ConfigurableUserClassFilePermissions getOther();

    /**
     * Modifies the permissions all other users (non-owner, non-group) have
     * for the file/directory.
     * <p>
     * For further details on permissions see {@link ConfigurableUserClassFilePermissions}.
     * <p>
     * Note that the provided configuration action only applies incremental modifications on top of whatever permission
     * the user has at the moment and that the default values permissions start out are different for files and directories
     * (see {@link UserClassFilePermissions}).
     */
    void other(Action<? super ConfigurableUserClassFilePermissions> configureAction);

    /**
     * Sets Unix style permissions. Accept values in two styles of notation:
     * <ul>
     *     <li>NUMERIC notation: uses 3 octal (base-8) digits representing permissions for the 3 categories of users; for example "755"</li>
     *     <li>SYMBOLIC notation: uses 3 sets of 3 characters, each set representing the permissions for one of the user categories; for example "rwxr-xr-x"</li>
     * </ul>
     * <p>
     * The NUMERIC notation consist of 3 digits having values from 0 to 7.
     * 1st digit represents the OWNER, 2nd represents the GROUP while the 3rd represents OTHER users.
     * <p>
     * Each of the digits is the sum of its component bits in the binary numeral system.
     * Each of the 3 bits represents a permission.
     * 1st bit is the READ bit, adds 4 to the digit (binary 100).
     * 2nd bit is the WRITE bit, adds 2 to the digit (binary 010).
     * 3rd bit is the EXECUTE bit, adds 1 to the digit (binary 001).
     * <p>
     * See the examples below.
     * <p>
     * NOTE: providing the 3 numeric digits can also be done in the octal literal form, so "0740" will
     * be handled identically to "740".
     * <p>
     * The SYMBOLIC notation consists of 3 sets of 3 characters. The 1st set represents the OWNER,
     * the 2nd set represents the GROUP, the 3rd set represents OTHER users.
     * <p>
     * Each of the tree characters represents the read, write and execute permissions:
     * <ul>
     *     <li><code>r</code> if READING is permitted, <code>-</code> if it is not; must be 1st in the set</li>
     *     <li><code>w</code> if WRITING is permitted, <code>-</code> if it is not; must be 2nd in the set</li>
     *     <li><code>x</code> if EXECUTING is permitted, <code>-</code> if it is not; must be 3rd in the set</li>
     * </ul>
     * <p>
     * Examples:
     * <table>
     *   <tr>
     *     <th>Numeric</th>
     *     <th>Symbolic</th>
     *     <th>Meaning</th>
     *   </tr>
     *   <tr>
     *     <td>000</td>
     *     <td>---------</td>
     *     <td>no permissions</td>
     *   </tr>
     *   <tr>
     *     <td>700</td>
     *     <td>rwx------</td>
     *     <td>read, write &amp; execute only for owner</td>
     *   </tr>
     *   <tr>
     *     <td>770</td>
     *     <td>rwxrwx---</td>
     *     <td>read, write &amp; execute for owner and group</td>
     *   </tr>
     *   <tr>
     *     <td>111</td>
     *     <td>--x--x--x</td>
     *     <td>execute</td>
     *   </tr>
     *   <tr>
     *     <td>222</td>
     *     <td>-w--w--w-</td>
     *     <td>write</td>
     *   </tr>
     *   <tr>
     *     <td>333</td>
     *     <td>-wx-wx-wx</td>
     *     <td>write &amp; execute</td>
     *   </tr>
     *   <tr>
     *     <td>444</td>
     *     <td>r--r--r--</td>
     *     <td>read</td>
     *   </tr>
     *   <tr>
     *     <td>555</td>
     *     <td>r-xr-xr-x</td>
     *     <td>read &amp; execute</td>
     *   </tr>
     *   <tr>
     *     <td>666</td>
     *     <td>rw-rw-rw-</td>
     *     <td>read &amp; write</td>
     *   </tr>
     *   <tr>
     *     <td>740</td>
     *     <td>rwxr-----</td>
     *     <td>owner can read, write &amp; execute; group can only read; others have no permissions</td>
     *   </tr>
     * </table>
     * <p>
     * An example usage of this method would be configuring a copy task and explicitly specifying the destination file permissions:
     * <pre>
     * from(...)
     * into(...)
     * filePermissions { unix("r--r--r--") }
     * </pre>
     */
    void unix(String unixNumericOrSymbolic);

    /**
     * Sets Unix style numeric permissions. See {@link #unix(String)} for details.
     */
    void unix(int unixNumeric);

}
