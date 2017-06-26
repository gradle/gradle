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

import org.gradle.cache.PersistentStateCache;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.Factory;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;

import java.io.File;

class DefaultPersistentScopeIdLoader implements PersistentScopeIdLoader {

    // Both of these values are effectively part of a cross Gradle version contract.
    // Do not change them.
    // If they change, continuity of the IDs will be broken.
    private static final String USER_ID_FILENAME = "user-id.txt";
    private static final String WORKSPACE_ID_FILENAME = "workspace-id.txt";

    private final ProjectCacheDir projectCacheDir;
    private final CacheScopeMapping cacheScopeMapping;
    private final Factory<UniqueId> generator;
    private final PersistentScopeIdStoreFactory storeFactory;

    DefaultPersistentScopeIdLoader(ProjectCacheDir projectCacheDir, CacheScopeMapping cacheScopeMapping, PersistentScopeIdStoreFactory storeFactory, Factory<UniqueId> generator) {
        this.projectCacheDir = projectCacheDir;
        this.cacheScopeMapping = cacheScopeMapping;
        this.generator = generator;
        this.storeFactory = storeFactory;
    }

    @Override
    public UserScopeId getUser() {
        UniqueId uniqueId = get(new ScopeParams(userScopeCacheScopeMarker(), USER_ID_FILENAME, "User ID"));
        return new UserScopeId(uniqueId);
    }

    @Override
    public WorkspaceScopeId getWorkspace() {
        UniqueId uniqueId = get(new ScopeParams(workspaceScopeCacheScopeMarker(), WORKSPACE_ID_FILENAME, "Workspace ID"));
        return new WorkspaceScopeId(uniqueId);
    }

    // This method is effectively part of a cross Gradle version contract.
    // User scope is expected to be persisted in the global cache since 4.0.
    private Object userScopeCacheScopeMarker() {
        return null;
    }

    // This method is effectively part of a cross Gradle version contract.
    // Workspace scope is expected to be persisted in the project cache dir since 4.0.
    private File workspaceScopeCacheScopeMarker() {
        return projectCacheDir.getDir();
    }

    private UniqueId get(ScopeParams params) {
        PersistentStateCache<UniqueId> store = store(params);

        return store.maybeUpdate(new PersistentStateCache.UpdateAction<UniqueId>() {
            @Override
            public UniqueId update(UniqueId oldValue) {
                if (oldValue == null) {
                    return generator.create();
                } else {
                    return oldValue;
                }
            }
        });
    }

    private PersistentStateCache<UniqueId> store(ScopeParams params) {
        File file = cacheScopeMapping.getBaseDirectory(
            params.cacheScopeMarker, params.fileName, VersionStrategy.SharedCache
        );

        return storeFactory.create(file, params.description);
    }

    private static class ScopeParams {
        private final Object cacheScopeMarker;
        private final String fileName;
        private final String description;

        private ScopeParams(Object cacheScopeMarker, String fileName, String description) {
            this.cacheScopeMarker = cacheScopeMarker;
            this.fileName = fileName;
            this.description = description;
        }
    }
}
