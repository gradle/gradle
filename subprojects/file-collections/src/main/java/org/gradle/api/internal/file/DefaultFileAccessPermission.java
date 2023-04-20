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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class DefaultFileAccessPermission extends AbstractImmutableFileAccessPermission implements FileAccessPermissionInternal {

    @Inject
    public DefaultFileAccessPermission(int unixNumeric) {
        getRead().value(isRead(unixNumeric));
        getWrite().value(isWrite(unixNumeric));
        getExecute().value(isExecute(unixNumeric));
    }

    @Override
    public abstract Property<Boolean> getRead();

    @Override
    public abstract Property<Boolean> getWrite();

    @Override
    public abstract Property<Boolean> getExecute();

    @Override
    public Provider<Integer> toUnixNumeric() {
        getRead().finalizeValue();
        getWrite().finalizeValue();
        getExecute().finalizeValue();
        return super.toUnixNumeric();
    }

    @Override
    public void unix(String permission) {
        getRead().value(isRead(permission));
        getWrite().value(isWrite(permission));
        getExecute().value(isExecute(permission));
    }

    private static boolean isRead(String permission) {
        if (permission.length() == 1) {
            return isRead(toUnixNumericPermissions(permission));
        } else {
            return isRead(permission.charAt(0));
        }
    }

    private static boolean isWrite(String permission) {
        if (permission.length() == 1) {
            return isWrite(toUnixNumericPermissions(permission));
        } else {
            return isWrite(permission.charAt(1));
        }
    }

    private static boolean isExecute(String permission) {
        if (permission.length() == 1) {
            return isExecute(toUnixNumericPermissions(permission));
        } else {
            return isExecute(permission.charAt(2));
        }
    }

    private static int toUnixNumericPermissions(String permissions) {
        try {
            return Integer.parseInt(permissions, 8);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Can't be parsed as octal number.");
        }
    }
}
