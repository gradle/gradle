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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.annotation.Nullable;
import javax.inject.Inject;


/**
 * Configuration object for the local directory build cache.
 *
 * @since 3.5
 */
public abstract class DirectoryBuildCache extends AbstractBuildCache {
    private int removeUnusedEntriesAfterDays = 7;

    /**
     * The directory to use to store the build cache.
     */
    @Optional
    @ReplacesEagerProperty(adapter = DirectoryAdapter.class)
    public abstract DirectoryProperty getDirectory();

    /**
     * Returns the number of days after unused entries are garbage collected. Defaults to 7 days.
     *
     * @since 4.6
     * @deprecated this is superseded by <code>CacheConfigurations.buildCache.removeUnusedEntriesAfterDays</code>
     */
    @Deprecated
    public int getRemoveUnusedEntriesAfterDays() {
        return removeUnusedEntriesAfterDays;
    }

    /**
     * Sets the number of days after unused entries are garbage collected. Defaults to 7 days.
     *
     * Must be greater than 1.
     *
     * @since 4.6
     * @deprecated this is superseded by <code>CacheConfigurations.buildCache.removeUnusedEntriesAfterDays</code>
     */
    @Deprecated
    public void setRemoveUnusedEntriesAfterDays(int removeUnusedEntriesAfterDays) {
        if (removeUnusedEntriesAfterDays < 1) {
            throw new IllegalArgumentException("Directory build cache needs to retain entries for at least a day.");
        }
        DeprecationLogger.deprecateProperty(DirectoryBuildCache.class, "removeEntriesAfterDays")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "directory_build_cache_retention_deprecated")
            .nagUser();

        this.removeUnusedEntriesAfterDays = removeUnusedEntriesAfterDays;
    }

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
