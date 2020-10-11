/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.std;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.internal.execution.workspace.impl.DefaultImmutableWorkspaceProvider;
import org.gradle.internal.file.FileAccessTimeJournal;

public class DependenciesAccessorsWorkspace extends DefaultImmutableWorkspaceProvider {
    public DependenciesAccessorsWorkspace(CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, FileAccessTimeJournal fileAccessTimeJournal, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory, StringInterner stringInterner) {
        super("dependencies-accessors", cacheScopeMapping.getBaseDirectory(null, "dependencies-accessors", VersionStrategy.CachePerVersion), cacheRepository, fileAccessTimeJournal, inMemoryCacheDecoratorFactory, stringInterner);
    }
}
