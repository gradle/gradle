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

package org.gradle.api.internal.initialization;

import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;

import java.io.File;
import java.util.function.Supplier;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public final class FileLocking {
    @SuppressWarnings("try")
    public static <T> T withExclusiveFileLockFor(
        FileLockManager fileLockManager,
        File target,
        String displayName,
        Supplier<T> supplier
    ) {
        try (FileLock ignored = fileLockManager.lock(target, LOCK_OPTIONS, displayName)) {
            return supplier.get();
        }
    }

    private static final LockOptions LOCK_OPTIONS = mode(FileLockManager.LockMode.Exclusive).useCrossVersionImplementation();
}
