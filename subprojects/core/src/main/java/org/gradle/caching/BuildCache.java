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

package org.gradle.caching;

/**
 * Cache protocol interface to be implemented by a build cache service.
 *
 * <p>
 *     Build cache implementations should report a non-fatal failure as a {@link BuildCacheException}.
 *     Non-fatal failures could include failing to retrieve a cache entry or unsuccessfully completing an upload a new cache entry.
 *     Gradle will not fail the build when catching a {@code BuildCacheException}, but it may disable caching for the build if too
 *     many failures occur.
 * </p>
 * <p>
 *     All other failures will be considered fatal and cause the Gradle build to fail.
 *     Fatal failures could include failing to read or write cache entries due to file permissions, authentication or corruption errors.
 * </p>
 * @since 3.3
 *
 * @deprecated Use {@link BuildCacheService} instead.
 */
@Deprecated
public interface BuildCache extends BuildCacheService {
}
