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

import org.gradle.api.file.UserClassFilePermissions;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

public class DefaultFilePermissions extends AbstractFilePermissions {

    public static final DefaultFilePermissions DEFAULT_FILE_PERMISSIONS = new DefaultFilePermissions(FileSystem.DEFAULT_FILE_MODE);

    public static final DefaultFilePermissions DEFAULT_DIR_PERMISSIONS = new DefaultFilePermissions(FileSystem.DEFAULT_DIR_MODE);

    private final UserClassFilePermissions user;

    private final UserClassFilePermissions group;

    private final UserClassFilePermissions other;

    public DefaultFilePermissions(int unixNumeric) {
        user = new DefaultUserClassFilePermissions(getUserPartOf(unixNumeric));
        group = new DefaultUserClassFilePermissions(getGroupPartOf(unixNumeric));
        other = new DefaultUserClassFilePermissions(getOtherPartOf(unixNumeric));
    }

    @Override
    public UserClassFilePermissions getUser() {
        return user;
    }

    @Override
    public UserClassFilePermissions getGroup() {
        return group;
    }

    @Override
    public UserClassFilePermissions getOther() {
        return other;
    }
}
