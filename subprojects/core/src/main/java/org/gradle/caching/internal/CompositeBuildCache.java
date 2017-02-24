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

import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.caching.configuration.BuildCache;

/**
 * A {@link BuildCache} which can pull from multiple other build caches and push to one other build cache.
 *
 * This is used if both a local and a remote cache is configured in {@link org.gradle.caching.configuration.BuildCacheConfiguration}
 * but cannot be directly configured by the user.
 */
public class CompositeBuildCache extends AbstractBuildCache {
    private BuildCache local;
    private BuildCache remote;

    public CompositeBuildCache() {
        setPush(true);
    }

    public BuildCache getLocal() {
        return local;
    }

    public void setLocal(BuildCache local) {
        this.local = local;
    }

    public BuildCache getRemote() {
        return remote;
    }

    public void setRemote(BuildCache remote) {
        this.remote = remote;
    }
}
