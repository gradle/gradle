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
import org.gradle.caching.local.internal.LocalBuildCacheService;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;
import java.util.function.Function;

public class NullLocalBuildCacheServiceHandle implements LocalBuildCacheServiceHandle {

    public static final LocalBuildCacheServiceHandle INSTANCE = new NullLocalBuildCacheServiceHandle();

    private NullLocalBuildCacheServiceHandle() {
    }

    @Nullable
    @Override
    public LocalBuildCacheService getService() {
        return null;
    }

    @Override
    public Optional<BuildCacheLoadResult> maybeLoad(BuildCacheKey key, Function<File, BuildCacheLoadResult> unpackFunction) {
        return Optional.empty();
    }

    @Override
    public boolean canStore() {
        return false;
    }

    @Override
    public boolean maybeStore(BuildCacheKey key, File file) {
        return false;
    }

    @Override
    public void close() {

    }
}
