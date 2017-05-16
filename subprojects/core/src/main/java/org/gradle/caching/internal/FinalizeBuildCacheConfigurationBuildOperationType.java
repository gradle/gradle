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

package org.gradle.caching.internal;

import org.gradle.api.Nullable;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Map;

/**
 * The transformation of the user's build cache config, to the effective configuration.
 *
 * This operation should occur some time after the configuration phase.
 * In practice, it will fire as part of bootstrapping the execution of the first task to execute.
 *
 * This operation should always be executed, regardless of whether caching is enabled/disabled.
 * That is, determining enabled-ness is part of “finalizing”.
 * However, if the build fails during configuration or task graph assembly, it will not be emitted.
 * It must fire before any build cache is used.
 *
 * See BuildCacheServiceProvider.
 *
 * @since 4.0
 */
public final class FinalizeBuildCacheConfigurationBuildOperationType implements BuildOperationType<FinalizeBuildCacheConfigurationBuildOperationType.Details, FinalizeBuildCacheConfigurationBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

    }

    @UsedByScanPlugin
    public interface Result {

        boolean isEnabled();

        boolean isLocalEnabled();

        boolean isRemoteEnabled();

        @Nullable
        BuildCacheDescription getLocal();

        @Nullable
        BuildCacheDescription getRemote();

        interface BuildCacheDescription {

            /**
             * The class name of the DSL configuration type.
             *
             * e.g. {@link org.gradle.caching.local.DirectoryBuildCache}
             */
            String getClassName();

            /**
             * The human friendly description of the type (e.g. "HTTP", "directory")
             *
             * @see org.gradle.caching.BuildCacheServiceFactory.Describer#type(String)
             */
            String getType();

            /**
             * Whether push was enabled.
             */
            boolean isPush();

            /**
             * The advertised config parameters of the cache.
             * No null values or keys.
             * Ordered by key lexicographically.
             *
             * @see org.gradle.caching.BuildCacheServiceFactory.Describer#config(String, String)
             */
            Map<String, String> getConfig();

        }

    }

    static class DetailsImpl implements Details {

    }

    static class ResultImpl implements Result {

        private final boolean enabled;

        private final boolean localEnabled;

        private final BuildCacheDescription local;

        private final boolean remoteEnabled;

        private final BuildCacheDescription remote;

        ResultImpl(boolean enabled, boolean localEnabled, boolean remoteEnabled, @Nullable BuildCacheDescription local, @Nullable BuildCacheDescription remote) {
            this.enabled = enabled;
            this.localEnabled = localEnabled;
            this.remoteEnabled = remoteEnabled;
            this.local = local;
            this.remote = remote;
        }

        static Result disabled() {
            return new ResultImpl(false, false, false, null, null);
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public boolean isLocalEnabled() {
            return localEnabled;
        }

        @Override
        public boolean isRemoteEnabled() {
            return remoteEnabled;
        }

        @Override
        @Nullable
        public BuildCacheDescription getLocal() {
            return local;
        }

        @Override
        @Nullable
        public BuildCacheDescription getRemote() {
            return remote;
        }

    }

    private FinalizeBuildCacheConfigurationBuildOperationType() {
    }

}
