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

package org.gradle.caching.internal.controller.service;

import org.gradle.caching.BuildCacheService;
import org.gradle.caching.local.internal.LocalBuildCacheService;

import javax.annotation.Nullable;

public final class BuildCacheServicesConfiguration {

    private final LocalBuildCacheService local;
    private final boolean localPush;

    private final BuildCacheService remote;
    private final boolean remotePush;

    public BuildCacheServicesConfiguration(
        @Nullable LocalBuildCacheService local,
        boolean localPush,
        @Nullable BuildCacheService remote,
        boolean remotePush
    ) {
        this.remote = remote;
        this.remotePush = remotePush;
        this.local = local;
        this.localPush = localPush;
    }

    @Nullable
    public LocalBuildCacheService getLocal() {
        return local;
    }

    public boolean isLocalPush() {
        return localPush;
    }

    @Nullable
    public BuildCacheService getRemote() {
        return remote;
    }

    public boolean isRemotePush() {
        return remotePush;
    }
}
