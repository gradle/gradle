/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl

import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.BuildCache
import org.gradle.caching.configuration.BuildCacheConfiguration
import kotlin.reflect.KClass


/**
 * Registers a custom build cache type.
 *
 * @param T Configuration type used to provide parameters to a [org.gradle.caching.BuildCacheService]
 * @param buildCacheServiceFactoryType Implementation type of [BuildCacheServiceFactory] that is used to create a [org.gradle.caching.BuildCacheService]
 *
 * @see BuildCacheConfiguration.registerBuildCacheService
 */
inline fun <reified T : BuildCache> BuildCacheConfiguration.registerBuildCacheService(buildCacheServiceFactoryType: KClass<out BuildCacheServiceFactory<in T>>) {
    registerBuildCacheService(T::class.java, buildCacheServiceFactoryType.java)
}


/**
 * Configures a remote cache with the given type.
 *
 * If a remote build cache has already been configured with a different type, this method replaces it.
 *
 * Storing ("push") in the remote build cache is disabled by default.
 *
 * @param T the type of remote cache to configure.
 *
 * @see BuildCacheConfiguration.remote
 */
inline fun <reified T : BuildCache> BuildCacheConfiguration.remote(): T =
    remote(T::class.java)


/**
 * Configures a remote cache with the given type.
 *
 * If a remote build cache has already been configured with a **different** type, this method replaces it.
 *
 * If a remote build cache has already been configured with the **same**, this method configures it.
 *
 * Storing ("push") in the remote build cache is disabled by default.
 *
 * @param T the type of remote cache to configure.
 * @param configuration the configuration to execute against the remote cache.
 *
 * @see BuildCacheConfiguration.remote
 */
inline fun <reified T : BuildCache> BuildCacheConfiguration.remote(noinline configuration: T.() -> Unit): T =
    remote(T::class.java, configuration)
