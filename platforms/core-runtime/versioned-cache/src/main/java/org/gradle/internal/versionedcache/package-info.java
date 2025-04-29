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

/**
 * Infrastructure to manage version-specific Gradle caches. Version-specific caches include
 * <ul>
 *     <li>Caches that are tied to a specific Gradle version, typically residing in directories named after version, e.g. {@code .gradle/8.11.1-rc-1/...}.
 *     See {@link org.gradle.internal.versionedcache.VersionSpecificCacheDirectoryScanner}.</li>
 *     <li>Caches that change their version less often, but their version is still tied to a Gradle version range.
 *     See {@link org.gradle.internal.versionedcache.CacheVersionMapping}.</li>
 * </ul>
 */
@NullMarked
package org.gradle.internal.versionedcache;

import org.jspecify.annotations.NullMarked;
