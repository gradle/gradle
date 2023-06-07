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

package org.gradle.api.internal.file;

import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.ConfigurableUserClassFilePermissions;

public interface ConfigurableUserClassFilePermissionsInternal extends ConfigurableUserClassFilePermissions {

    /**
     * Sets the permission for a specific class of users from a PARTIAL Unix-style symbolic permission
     * (i.e. 3 alphanumeric characters; see {@link ConfigurableFilePermissions#unix(String)} for details).
     */
    void unix(String unixSymbolic);

    /**
     * Sets the permission for a specific class of users based on a PARTIAL Unix-style numeric permission
     * (i.e. a number between 0 and 7; see {@link ConfigurableFilePermissions#unix(String)} for details).
     */
    void unix(int unixNumeric);

}
