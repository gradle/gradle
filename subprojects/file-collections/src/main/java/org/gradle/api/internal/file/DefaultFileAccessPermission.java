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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.util.function.Function;

public abstract class DefaultFileAccessPermission extends AbstractImmutableFileAccessPermission implements FileAccessPermissionInternal {

    @Inject
    public DefaultFileAccessPermission(int unixNumeric) {
        getRead().value(isRead(unixNumeric)).finalizeValueOnRead();
        getWrite().value(isWrite(unixNumeric)).finalizeValueOnRead();
        getExecute().value(isExecute(unixNumeric)).finalizeValueOnRead();
    }

    @Override
    public abstract Property<Boolean> getRead();

    @Override
    public abstract Property<Boolean> getWrite();

    @Override
    public abstract Property<Boolean> getExecute();

    @Override
    public void unix(Provider<String> permission, int index) {
        getRead().set(decode(permission, index, DefaultFileAccessPermission::isRead, DefaultFileAccessPermission::isRead));
        getWrite().set(decode(permission, index, DefaultFileAccessPermission::isWrite, DefaultFileAccessPermission::isWrite));
        getExecute().set(decode(permission, index, DefaultFileAccessPermission::isExecute, DefaultFileAccessPermission::isExecute));
    }

    private static Provider<Boolean> decode(
        Provider<String> permission,
        int index,
        Function<Integer, Boolean> numericDecoder,
        Function<String, Boolean> symbolicDecoder
    ) {
        return permission.map(p -> {
            try {
                if (p.length() == 3) {
                    return numericDecoder.apply(toUnixNumericPermissions(p.substring(index, index + 1)));
                } else {
                    return symbolicDecoder.apply(p.substring(3 * index, 3 * (index + 1)));
                }
            } catch (IllegalArgumentException cause) {
                throw new InvalidUserDataException("'" + p + "' isn't a proper Unix permission. " + cause.getMessage());
            }
        });
    }

    private static int toUnixNumericPermissions(String permissions) {
        try {
            return Integer.parseInt(permissions, 8);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Can't be parsed as octal number.");
        }
    }
}
