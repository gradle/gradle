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

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import javax.annotation.Nullable;

public class NullBuildCacheServiceHandle implements BuildCacheServiceHandle {

    public static final BuildCacheServiceHandle INSTANCE = new NullBuildCacheServiceHandle();

    @Nullable
    @Override
    public BuildCacheService getService() {
        return null;
    }

    @Override
    public boolean canLoad() {
        return false;
    }

    @Override
    public void load(BuildCacheKey key, LoadTarget loadTarget) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canStore() {
        return false;
    }

    @Override
    public void store(BuildCacheKey key, StoreTarget storeTarget) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {

    }

}
