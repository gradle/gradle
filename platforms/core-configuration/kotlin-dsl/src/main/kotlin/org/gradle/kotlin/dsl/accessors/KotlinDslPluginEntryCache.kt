/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.accessors

import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.PluginEntry
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.PluginEntryCache
import java.io.File
import java.lang.AutoCloseable
import java.util.function.Supplier

@ServiceScope(Scope.BuildTree::class)
internal class KotlinDslPluginEntryCache(
    cacheBuilderFactory: BuildTreeScopedCacheBuilderFactory,
    inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
    private val checksums: ChecksumService,
) : PluginEntryCache, AutoCloseable {

    private val cacheBuilder: PersistentCache =
        cacheBuilderFactory
            .createCacheBuilder("kotlin-dsl-plugin-entries")
            .withDisplayName("Kotlin DSL Plugin Entries")
            .withInitialLockMode(FileLockManager.LockMode.OnDemandEagerRelease)
            .open()

    private val cache: IndexedCache<HashCode, List<PluginEntry>> =
        cacheBuilder.createIndexedCache(
            IndexedCacheParameters.of("jar-entries", HashCodeSerializer(), PluginEntrySerializer)
                .withCacheDecorator(
                    inMemoryCacheDecoratorFactory.decorator(
                        /* maxEntriesToKeepInMemory = */ 1000,
                        /* cacheInMemoryForShortLivedProcesses = */ true
                    )
                )
        )

    override fun computeIfAbsent(
        jar: File,
        producer: (File) -> List<PluginEntry>
    ): List<PluginEntry> =
        cache.get(checksums.md5(jar), Supplier {
            producer.invoke(jar)
        })

    override fun close() {
        cacheBuilder.close()
    }

    private object PluginEntrySerializer : Serializer<List<PluginEntry>> {

        override fun read(decoder: Decoder): List<PluginEntry> =
            buildList {
                repeat(decoder.readSmallInt()) {
                    add(PluginEntry(decoder.readString(), decoder.readString()))
                }
            }

        override fun write(encoder: Encoder, value: List<PluginEntry>) {
            encoder.writeSmallInt(value.size)
            value.forEach { entry ->
                encoder.writeString(entry.pluginId)
                encoder.writeString(entry.implementationClass)
            }
        }
    }
}
