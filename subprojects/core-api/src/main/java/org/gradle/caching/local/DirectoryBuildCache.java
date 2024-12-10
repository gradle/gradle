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

package org.gradle.caching.local;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Optional;
import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;


/**
 * Configuration object for the local directory build cache.
 *
 * @since 3.5
 */
public abstract class DirectoryBuildCache extends AbstractBuildCache {

    /**
     * The directory to use to store the build cache.
     */
    @Optional
    @ReplacesEagerProperty(adapter = DirectoryAdapter.class)
    public abstract DirectoryProperty getDirectory();

    @Inject
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed") // used only for adapter and backward compatibility
    protected abstract PathToFileResolver getFileResolver();

    static class DirectoryAdapter {
        @BytecodeUpgrade
        @Nullable
        static Object getDirectory(DirectoryBuildCache buildCache) {
            return buildCache.getDirectory().getAsFile().getOrNull();
        }

        @SuppressWarnings("DataFlowIssue") // directory can be null and resolver handles null
        @BytecodeUpgrade
        static void setDirectory(DirectoryBuildCache buildCache, @Nullable Object directory) {
            buildCache.getDirectory().set(buildCache.getFileResolver().resolve(directory));
        }
    }
}
