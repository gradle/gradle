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

package org.gradle.caching.internal.controller;

import org.gradle.api.Nullable;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import java.io.File;

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
    public <T> T doLoad(BuildCacheLoadCommand<T> command) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canStore() {
        return false;
    }

    @Override
    public void doStore(BuildCacheStoreCommand command) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void doStore(BuildCacheKey key, File file, BuildCacheStoreCommand.Result storeResult) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {

    }

}
