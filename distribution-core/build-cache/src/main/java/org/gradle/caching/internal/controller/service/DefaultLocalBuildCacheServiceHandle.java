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

import org.gradle.api.Action;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.local.internal.LocalBuildCacheService;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultLocalBuildCacheServiceHandle implements LocalBuildCacheServiceHandle {

    private final LocalBuildCacheService service;
    private final boolean pushEnabled;

    public DefaultLocalBuildCacheServiceHandle(LocalBuildCacheService service, boolean pushEnabled) {
        this.service = service;
        this.pushEnabled = pushEnabled;
    }

    @Nullable
    @Override
    public LocalBuildCacheService getService() {
        return service;
    }

    @Override
    public boolean canLoad() {
        return true;
    }

    @Override
    public void load(BuildCacheKey key, Action<? super File> reader) {
        service.loadLocally(key, reader);
    }

    @Override
    public boolean canStore() {
        return pushEnabled;
    }

    @Override
    public void store(BuildCacheKey key, File file) {
        service.storeLocally(key, file);
    }

    @Override
    public void close() {
        service.close();
    }
}
