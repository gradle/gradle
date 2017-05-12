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
import org.gradle.internal.progress.BuildOperationDetails;

import java.util.SortedMap;

import static org.gradle.caching.BuildCacheServiceFactory.Describer;

/**
 * Represents the transformation of the user's build cache config, to the effective configuration.
 *
 * This operation should occur some time after the configuration phase.
 * In practice, it will fire as part of bootstrapping the execution of the first task to execute.
 *
 * This operation should always be executed, regardless of whether caching is enabled/disabled.
 * That is, determining enabled-ness is part of “finalizing”.
 * However, if the build fails during configuration or task graph assembly, it will not be emitted.
 * It must fire before any build cache is used.
 *
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @see BuildCacheServiceProvider
 * @since 4.0
 */
public final class FinalizeBuildCacheConfigurationDetails implements BuildOperationDetails<FinalizeBuildCacheConfigurationDetails.Result> {

    /**
     * Represents the effective build cache configuration.
     *
     * Null values for local and remote represent a completely disabled state.
     */
    public static class Result {

        public interface BuildCacheDescription {

            /**
             * The class name of the DSL configuration type.
             *
             * e.g. {@link org.gradle.caching.local.DirectoryBuildCache}
             */
            String getClassName();

            /**
             * The human friendly description of the type (e.g. "HTTP", "directory")
             *
             * @see Describer#type(String)
             */
            String getType();

            /**
             * Whether push was enabled.
             */
            boolean isPush();

            /**
             * May contain null values.
             * Entries with null values are to be treated as {@code true} flag values.
             *
             * @see Describer#config(String, String)
             */
            SortedMap<String, String> getConfig();

        }

        private final boolean enabled;

        private final boolean localEnabled;

        private final BuildCacheDescription local;

        private final boolean remoteEnabled;

        private final BuildCacheDescription remote;

        public Result(boolean enabled, boolean localEnabled, boolean remoteEnabled, @Nullable BuildCacheDescription local, @Nullable BuildCacheDescription remote) {
            this.enabled = enabled;
            this.localEnabled = localEnabled;
            this.remoteEnabled = remoteEnabled;
            this.local = local;
            this.remote = remote;
        }

        public static Result buildCacheConfigurationDisabled() {
            return new FinalizeBuildCacheConfigurationDetails.Result(false, false, false, null, null);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isLocalEnabled() {
            return localEnabled;
        }

        public boolean isRemoteEnabled() {
            return remoteEnabled;
        }

        @Nullable // if not enabled
        public BuildCacheDescription getLocal() {
            return local;
        }

        @Nullable // if not enabled
        public BuildCacheDescription getRemote() {
            return remote;
        }
    }

}
