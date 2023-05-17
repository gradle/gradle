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

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.UserClassFilePermissions;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

import static org.gradle.internal.nativeintegration.filesystem.FileSystem.DEFAULT_DIR_MODE;
import static org.gradle.internal.nativeintegration.filesystem.FileSystem.DEFAULT_FILE_MODE;

public class DefaultFilePermissions extends AbstractImmutableFilePermissions implements FilePermissions {

    public static int getDefaultUnixNumeric(boolean isDirectory) {
        return isDirectory ? DEFAULT_DIR_MODE : DEFAULT_FILE_MODE;
    }

    private final UserClassFilePermissionsInternal user;

    private final UserClassFilePermissionsInternal group;

    private final UserClassFilePermissionsInternal other;

    @Inject
    public DefaultFilePermissions(ObjectFactory objectFactory, int unixNumeric) {
        this.user = objectFactory.newInstance(DefaultUserClassFilePermissions.class, getUserPartOf(unixNumeric));
        this.group = objectFactory.newInstance(DefaultUserClassFilePermissions.class, getGroupPartOf(unixNumeric));
        this.other = objectFactory.newInstance(DefaultUserClassFilePermissions.class, getOtherPartOf(unixNumeric));
    }

    @Override
    public UserClassFilePermissions getUser() {
        return user;
    }

    @Override
    public void user(Action<? super UserClassFilePermissions> configureAction) {
        configureAction.execute(user);
    }

    @Override
    public UserClassFilePermissions getGroup() {
        return group;
    }

    @Override
    public void group(Action<? super UserClassFilePermissions> configureAction) {
        configureAction.execute(group);
    }

    @Override
    public UserClassFilePermissions getOther() {
        return other;
    }

    @Override
    public void other(Action<? super UserClassFilePermissions> configureAction) {
        configureAction.execute(other);
    }

    @Override
    public void unix(String permissions) {
        String normalizedPermissions = normalizeUnixPermissions(permissions);
        user.unix(normalizedPermissions, 0);
        group.unix(normalizedPermissions, 1);
        other.unix(normalizedPermissions, 2);
    }

    public void unix(int unixNumeric) {
        user.unix(getUserPartOf(unixNumeric));
        group.unix(getGroupPartOf(unixNumeric));
        other.unix(getOtherPartOf(unixNumeric));
    }

    private static String normalizeUnixPermissions(String p) {
        String trimmed = p.trim();
        if (trimmed.length() == 4 && trimmed.startsWith("0")) {
            return trimmed.substring(1);
        }
        if (trimmed.length() != 3 && trimmed.length() != 9) {
            throw new InvalidUserDataException("'" + p + "' isn't a proper Unix permission. Trimmed length must be either 3 (for numeric notation) or 9 (for symbolic notation).");
        }
        return trimmed;
    }
}
