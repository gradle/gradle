/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.cache.FileAccess;
import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.LockTimeoutException;
import org.gradle.internal.Factory;

import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public abstract class AbstractFileAccess implements FileAccess {
    public <T> T readFile(final Callable<? extends T> action) throws LockTimeoutException, FileIntegrityViolationException {
        return readFile(new Factory<T>() {
            public T create() {
                return uncheckedCall(action);
            }
        });
    }
}
