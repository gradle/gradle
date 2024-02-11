/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.scopeids;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.ObjectHolder;
import org.gradle.cache.internal.FileBackedObjectHolder;
import org.gradle.cache.internal.FileIntegrityViolationSuppressingObjectHolderDecorator;
import org.gradle.cache.internal.OnDemandFileAccess;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.id.UniqueId;

import java.io.File;

class PersistentScopeIdStoreFactory {

    private final Chmod chmod;
    private final FileLockManager fileLockManager;

    PersistentScopeIdStoreFactory(Chmod chmod, FileLockManager fileLockManager) {
        this.chmod = chmod;
        this.fileLockManager = fileLockManager;
    }

    ObjectHolder<UniqueId> create(File file, String description) {
        return new FileIntegrityViolationSuppressingObjectHolderDecorator<UniqueId>(
            new FileBackedObjectHolder<UniqueId>(
                file,
                new OnDemandFileAccess(file, description, fileLockManager),
                UniqueIdSerializer.INSTANCE,
                chmod
            )
        );
    }
}
