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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Nullable;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import java.io.Closeable;
import java.io.File;

public interface BuildCacheServiceHandle extends Closeable {

    @Nullable
    @VisibleForTesting
    BuildCacheService getService();

    boolean canLoad();

    <T> T doLoad(BuildCacheLoadCommand<T> command);

    boolean canStore();

    void doStore(BuildCacheStoreCommand command);

    void doStore(BuildCacheKey key, File file, BuildCacheStoreCommand.Result storeResult);

    @Override
    void close();
}
