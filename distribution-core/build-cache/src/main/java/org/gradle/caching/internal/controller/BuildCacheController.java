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

import org.gradle.caching.BuildCacheService;

import java.io.Closeable;
import java.util.Optional;

/**
 * Internal coordinator of build cache operations.
 *
 * Wraps user {@link BuildCacheService} implementations.
 */
public interface BuildCacheController extends Closeable {

    boolean isEnabled();

    boolean isEmitDebugLogging();

    <T> Optional<T> load(BuildCacheLoadCommand<T> command);

    void store(BuildCacheStoreCommand command);

}
