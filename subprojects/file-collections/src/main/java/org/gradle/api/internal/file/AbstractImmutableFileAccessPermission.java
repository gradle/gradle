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

import org.gradle.api.file.FileAccessPermissions;
import org.gradle.api.file.ImmutableFileAccessPermission;

public abstract class AbstractImmutableFileAccessPermission implements ImmutableFileAccessPermission {

    /**
     * Converts the user permission to a numeric Unix permission.
     * See {@link FileAccessPermissions#unix(String)} for details,
     * returned value is equivalent to one of the three octal digits.
     */
    protected int toUnixNumeric() {
        int unixNumeric = 0;
        unixNumeric += getRead().get() ? 4 : 0;
        unixNumeric += getWrite().get() ? 2 : 0;
        unixNumeric += getExecute().get() ? 1 : 0;
        return unixNumeric;
    }

    protected static boolean isRead(int unixNumeric) {
        return (unixNumeric & 4) >> 2 == 1;
    }

    protected static boolean isWrite(int unixNumeric) {
        return (unixNumeric & 2) >> 1 == 1;
    }

    protected static boolean isExecute(int unixNumeric) {
        return (unixNumeric & 1) == 1;
    }

}
