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

import org.gradle.api.file.FileAccessPermission;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class DefaultFileAccessPermission implements FileAccessPermission {

    @Inject
    public DefaultFileAccessPermission(int modeMask) {
        getRead().value(isRead(modeMask));
        getWrite().value(isWrite(modeMask));
        getExecute().value(isExecute(modeMask));
    }

    @Override
    public abstract Property<Boolean> getRead();

    @Override
    public abstract Property<Boolean> getWrite();

    @Override
    public abstract Property<Boolean> getExecute();

    private static boolean isRead(int modeMask) {
        return (modeMask & 4) >> 2 == 1;
    }

    private static boolean isWrite(int modeMask) {
        return (modeMask & 2) >> 1 == 1;
    }

    private static boolean isExecute(int modeMask) {
        return (modeMask & 1) == 1;
    }

    public int toMode() {
        int mode = 0;

        getRead().finalizeValue();
        mode += getRead().get() ? 4 : 0;

        getWrite().finalizeValue();
        mode += getWrite().get() ? 2 : 0;

        getExecute().finalizeValue();
        mode += getExecute().get() ? 1 : 0;

        return mode;
    }
}
