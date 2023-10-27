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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.watch.registry.FileWatcherRegistry;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Merge with BuildInputHierarchy?
 */
@ServiceScope(Scope.Global.class)
public class UnitOfWorkVfsChangesRegistry {

    private final Map<String, Set<String>> pathToIdentity = new ConcurrentHashMap<>();
    private final Set<String> changedIdentities = ConcurrentHashMap.newKeySet();

    /**
     * TODO: Figure out how much history we can keep, right now we just save the previous run
     */
    private final Set<String> previousUnitsOfWorkWithRegisteredInputs = ConcurrentHashMap.newKeySet();
    private final Set<String> unitsOfWorkWithRegisteredInputs = ConcurrentHashMap.newKeySet();

    /**
     * TODO: we could also resolve specific properties
     */
    public boolean hasAnyFileInputChanged(UnitOfWork.Identity identity) {
        return !previousUnitsOfWorkWithRegisteredInputs.contains(identity.getUniqueId()) || changedIdentities.contains(identity.getUniqueId());
    }

    public void registerFileInputs(UnitOfWork.Identity identity, Set<String> paths) {
        paths.forEach(path -> pathToIdentity.computeIfAbsent(path, __ -> ConcurrentHashMap.newKeySet()).add(identity.getUniqueId()));
        unitsOfWorkWithRegisteredInputs.add(identity.getUniqueId());
    }

    public void registerChange(FileWatcherRegistry.Type type, Path path) {
        Set<String> changedIds = pathToIdentity.get(path.toString());
        if (changedIds != null) {
            System.out.println("Registering change " + path + " for " + changedIds);
            changedIdentities.addAll(changedIds);
        }
    }

    public void invalidateAll() {
        pathToIdentity.clear();
        previousUnitsOfWorkWithRegisteredInputs.clear();
        unitsOfWorkWithRegisteredInputs.clear();
        changedIdentities.clear();
    }

    public void invalidateChanges() {
        System.out.println("Invalidating changes");
        previousUnitsOfWorkWithRegisteredInputs.clear();
        previousUnitsOfWorkWithRegisteredInputs.addAll(unitsOfWorkWithRegisteredInputs);
        unitsOfWorkWithRegisteredInputs.clear();
        changedIdentities.clear();
    }
}
