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

package org.gradle.api.internal.file.copy;

import org.gradle.api.Action;
import org.gradle.api.file.FileAccessPermission;
import org.gradle.api.file.FileAccessPermissions;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import javax.inject.Inject;

public class DefaultFileAccessPermissions implements FileAccessPermissions {

    private final DefaultFileAccessPermission user;

    private final DefaultFileAccessPermission group;

    private final DefaultFileAccessPermission other;

    @Inject
    public DefaultFileAccessPermissions(ObjectFactory objectFactory, boolean isDirectory) {
        int mode = isDirectory ? FileSystem.DEFAULT_DIR_MODE : FileSystem.DEFAULT_FILE_MODE;
        this.user = objectFactory.newInstance(DefaultFileAccessPermission.class, getUserMask(mode));
        this.group = objectFactory.newInstance(DefaultFileAccessPermission.class, getGroupMask(mode));
        this.other = objectFactory.newInstance(DefaultFileAccessPermission.class, getOtherMask(mode));
    }

    @Override
    public FileAccessPermission getUser() {
        return user;
    }

    @Override
    public void user(Action<? super FileAccessPermission> configureAction) {
        configureAction.execute(user);
    }

    @Override
    public FileAccessPermission getGroup() {
        return group;
    }

    @Override
    public void group(Action<? super FileAccessPermission> configureAction) {
        configureAction.execute(group);
    }

    @Override
    public FileAccessPermission getOther() {
        return other;
    }

    @Override
    public void other(Action<? super FileAccessPermission> configureAction) {
        configureAction.execute(other);
    }

    @Override
    public void all(Action<? super FileAccessPermission> configureAction) {
        configureAction.execute(user);
        configureAction.execute(group);
        configureAction.execute(other);
    }

    @Override
    public int toMode() {
        return 64 * user.toMode() + 8 * group.toMode() + other.toMode();
    }

    private static int getUserMask(int mode) {
        return (mode & 0_700) >> 6;
    }

    private static int getGroupMask(int mode) {
        return (mode & 0_070) >> 3;
    }

    private static int getOtherMask(int mode) {
        return mode & 0_007;
    }
}
