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

import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;

public class DefaultImmutableFileAccessPermission extends AbstractImmutableFileAccessPermission {

    private final Provider<Boolean> read;
    private final Provider<Boolean> write;
    private final Provider<Boolean> execute;

    public DefaultImmutableFileAccessPermission(int unixNumeric) {
        read = Providers.of(isRead(unixNumeric));
        write = Providers.of(isWrite(unixNumeric));
        execute = Providers.of(isExecute(unixNumeric));
    }

    @Override
    public Provider<Boolean> getRead() {
        return read;
    }

    @Override
    public Provider<Boolean> getWrite() {
        return write;
    }

    @Override
    public Provider<Boolean> getExecute() {
        return execute;
    }

}
