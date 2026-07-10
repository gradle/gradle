/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;

import java.util.concurrent.TimeUnit;

/**
 * Immutable counterpart to {@link CacheExpirationControl}.
 * <p>
 * Encapsulates all configurable caching rules of a {@link ResolutionStrategyInternal}.
 */
public interface CachePolicy {

    void setMutationValidator(MutationValidator validator);

    void setOffline();

    void setRefreshDependencies();

    void cacheChangingModulesFor(int value, TimeUnit units);

    void cacheDynamicVersionsFor(int value, TimeUnit units);

    CachePolicy copy();

    CacheExpirationControl asImmutable();

}
