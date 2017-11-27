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

/**
 * Represents the configuration of content filter for a repository. The filter
 * forms part of the identity of the repository. As such it is important not to allow
 * arbitrary code, but rather provide a "serializable" form of filter.
 */
public interface ImmutableRepositoryContentFilter {
    boolean isAlwaysProvidesMetadataForModules();

    void appendId(BuildCacheHasher hasher);
}
