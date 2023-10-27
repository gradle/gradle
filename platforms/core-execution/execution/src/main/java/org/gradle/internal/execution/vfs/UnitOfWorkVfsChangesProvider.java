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

package org.gradle.internal.execution.vfs;

import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;

@ServiceScope(Scopes.Gradle.class)
public class UnitOfWorkVfsChangesProvider implements Closeable {

    private final UnitOfWorkVfsChangesRegistry changesRegistry;

    @Inject
    public UnitOfWorkVfsChangesProvider(UnitOfWorkVfsChangesRegistry changesRegistry) {
        this.changesRegistry = changesRegistry;
    }

    public boolean hasAnyFileInputChanged(UnitOfWork.Identity identity) {
        return changesRegistry.hasAnyFileInputChanged(identity);
    }

    @Override
    public void close() throws IOException {
        changesRegistry.invalidateChanges();
    }
}
