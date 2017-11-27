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
package org.gradle.api.internal.artifacts.repositories;

import org.gradle.caching.internal.BuildCacheHasher;

public class DefaultRepositoryContentFilter implements RepositoryContentFilterInternal {
    private boolean alwaysProvidesMetadataForModules;

    @Override
    public void alwaysProvidesMetadataForModules() {
        alwaysProvidesMetadataForModules = true;
    }

    @Override
    public ImmutableRepositoryContentFilter asImmutable() {
        return new DefaultImmutableRepositoryContentFilter(alwaysProvidesMetadataForModules);
    }

    private static class DefaultImmutableRepositoryContentFilter implements ImmutableRepositoryContentFilter {
        private final boolean alwaysProvidesMetadataForModules;

        private DefaultImmutableRepositoryContentFilter(boolean alwaysProvidesMetadataForModules) {
            this.alwaysProvidesMetadataForModules = alwaysProvidesMetadataForModules;
        }

        @Override
        public boolean isAlwaysProvidesMetadataForModules() {
            return alwaysProvidesMetadataForModules;
        }

        @Override
        public void appendId(BuildCacheHasher hasher) {
            hasher.putBoolean(alwaysProvidesMetadataForModules);
        }
    }
}
