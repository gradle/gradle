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

import org.gradle.api.InvalidUserDataException;

import javax.inject.Inject;
import java.util.function.Function;

public abstract class DefaultUserClassFilePermissions extends AbstractImmutableUserClassFilePermissions implements UserClassFilePermissionsInternal {

    @Inject
    public DefaultUserClassFilePermissions(int unixNumeric) {
        setRead(isRead(unixNumeric));
        setWrite(isWrite(unixNumeric));
        setExecute(isExecute(unixNumeric));
    }

    @Override
    public void unix(String permission, int index) {
        setRead(decode(permission, index, DefaultUserClassFilePermissions::isRead, DefaultUserClassFilePermissions::isRead));
        setWrite(decode(permission, index, DefaultUserClassFilePermissions::isWrite, DefaultUserClassFilePermissions::isWrite));
        setExecute(decode(permission, index, DefaultUserClassFilePermissions::isExecute, DefaultUserClassFilePermissions::isExecute));
    }

    @Override
    public void unix(int unixNumeric) {
        setRead(isRead(unixNumeric));
        setWrite(isWrite(unixNumeric));
        setExecute(isExecute(unixNumeric));
    }

    private static boolean decode(
        String permission,
        int index,
        Function<Integer, Boolean> numericDecoder,
        Function<String, Boolean> symbolicDecoder
    ) {
        try {
            if (permission.length() == 3) {
                return numericDecoder.apply(toUnixNumericPermissions(permission.substring(index, index + 1)));
            } else {
                return symbolicDecoder.apply(permission.substring(3 * index, 3 * (index + 1)));
            }
        } catch (IllegalArgumentException cause) {
            throw new InvalidUserDataException("'" + permission + "' isn't a proper Unix permission. " + cause.getMessage());
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
