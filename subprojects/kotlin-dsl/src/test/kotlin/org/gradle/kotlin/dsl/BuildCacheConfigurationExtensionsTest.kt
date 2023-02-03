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

import org.gradle.api.Action
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.configuration.BuildCacheConfiguration
import org.gradle.caching.http.HttpBuildCache
import org.gradle.caching.local.DirectoryBuildCache
import org.gradle.caching.local.internal.DirectoryBuildCacheServiceFactory

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.junit.Test


class BuildCacheConfigurationExtensionsTest {

    @Test
    fun registerBuildCacheService() {

        val buildCache = mock<BuildCacheConfiguration>()
        doNothing().`when`(buildCache).registerBuildCacheService(any<Class<DirectoryBuildCache>>(), any<Class<BuildCacheServiceFactory<DirectoryBuildCache>>>())

        buildCache.registerBuildCacheService(DirectoryBuildCacheServiceFactory::class)

        inOrder(buildCache) {
            verify(buildCache).registerBuildCacheService(any<Class<DirectoryBuildCache>>(), any<Class<BuildCacheServiceFactory<DirectoryBuildCache>>>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun remote() {

        val buildCache = mock<BuildCacheConfiguration> {
            on { remote(any<Class<HttpBuildCache>>()) } doReturn mock<HttpBuildCache>()
            on { remote(any<Class<HttpBuildCache>>(), any<Action<HttpBuildCache>>()) } doReturn mock<HttpBuildCache>()
        }

        buildCache.remote<HttpBuildCache>()

        inOrder(buildCache) {
            verify(buildCache).remote(HttpBuildCache::class.java)
            verifyNoMoreInteractions()
        }

        buildCache.remote<HttpBuildCache> {}

        inOrder(buildCache) {
            verify(buildCache).remote(any<Class<HttpBuildCache>>(), any<Action<HttpBuildCache>>())
            verifyNoMoreInteractions()
        }
    }
}
