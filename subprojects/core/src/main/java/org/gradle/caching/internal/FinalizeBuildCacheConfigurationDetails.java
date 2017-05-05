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

/**
 * Details about the build cache configuration of a build.
 *
 * This class is intentionally internal and consumed by the build scan plugin.
 *
 * @since 4.0
 */
public final class FinalizeBuildCacheConfigurationDetails implements BuildOperationDetails<FinalizeBuildCacheConfigurationDetails.Result> {

    public static class Result {

        public interface BuildCache {

            String getClassName();

            String getType();

            boolean isEnabled();

            boolean isPush();

            SortedMap<String, String> getConfig();

        }

        private final boolean enabled;

        private final BuildCache local;

        private final BuildCache remote;

        public Result(boolean enabled, @Nullable BuildCache local, @Nullable BuildCache remote) {
            this.enabled = enabled;
            this.local = local;
            this.remote = remote;
        }

        public boolean isEnabled() {
            return enabled;
        }

        @Nullable
        public BuildCache getLocal() {
            return local;
        }

        @Nullable
        public BuildCache getRemote() {
            return remote;
        }
    }

}
