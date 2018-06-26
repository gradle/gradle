/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.ResourceHasher;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;

/**
 * Builds a {@link ClasspathSnapshot} for a runtime classpath.
 *
 * We take the contents of jar files, non jar files and directories into account.
 */
public class RuntimeClasspathRootFileCollectionSnapshotBuilder extends AbstractClasspathRootFileCollectionSnapshotBuilder {
    public RuntimeClasspathRootFileCollectionSnapshotBuilder(ResourceHasher classpathResourceHasher, ResourceSnapshotterCacheService cacheService, StringInterner stringInterner) {
        super(classpathResourceHasher, cacheService, stringInterner);
    }

    @Override
    protected FileContentSnapshot snapshotNonJarContents(FileContentSnapshot contentSnapshot) {
        return contentSnapshot;
    }
}
