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

import javax.inject.Inject;

public abstract class DefaultFileAccessPermission implements FileAccessPermissionInternal {

    @Inject
    public DefaultFileAccessPermission(int unixMode) {
        fromUnixNumeric(unixMode);
    }

    @Override
    public abstract Property<Boolean> getRead();

    @Override
    public abstract Property<Boolean> getWrite();

    @Override
    public abstract Property<Boolean> getExecute();

    @Override
    public int toUnixNumeric() {
        int unixNumeric = 0;

        getRead().finalizeValue();
        unixNumeric += getRead().get() ? 4 : 0;

        getWrite().finalizeValue();
        unixNumeric += getWrite().get() ? 2 : 0;

        getExecute().finalizeValue();
        unixNumeric += getExecute().get() ? 1 : 0;

        return unixNumeric;
    }

    @Override
    public void fromUnixNumeric(int unixNumeric) {
        getRead().value(isRead(unixNumeric));
        getWrite().value(isWrite(unixNumeric));
        getExecute().value(isExecute(unixNumeric));
    }

    @Override
    public void fromUnixSymbolic(String unixSymbolic) {
        getRead().value(isRead(unixSymbolic.charAt(0)));
        getWrite().value(isWrite(unixSymbolic.charAt(1)));
        getExecute().value(isExecute(unixSymbolic.charAt(2)));
    }

    private static boolean isRead(int unixNumeric) {
        return (unixNumeric & 4) >> 2 == 1;
    }

    private static boolean isRead(char symbol) {
        if (symbol == 'r') {
            return true;
        } else if (symbol == '-') {
            return false;
        } else {
            throw new IllegalArgumentException("'" + symbol + "' is not a valid Unix permission READ flag, must be 'r' or '-'.");
        }
    }

    private static boolean isWrite(int unixNumeric) {
        return (unixNumeric & 2) >> 1 == 1;
    }

    private static boolean isWrite(char symbol) {
        if (symbol == 'w') {
            return true;
        } else if (symbol == '-') {
            return false;
        } else {
            throw new IllegalArgumentException("'" + symbol + "' is not a valid Unix permission WRITE flag, must be 'w' or '-'.");
        }
    }

    private static boolean isExecute(int unixNumeric) {
        return (unixNumeric & 1) == 1;
    }

    private static boolean isExecute(char symbol) {
        if (symbol == 'x') {
            return true;
        } else if (symbol == '-') {
            return false;
        } else {
            throw new IllegalArgumentException("'" + symbol + "' is not a valid Unix permission EXECUTE flag, must be 'x' or '-'.");
        }
    }
}
