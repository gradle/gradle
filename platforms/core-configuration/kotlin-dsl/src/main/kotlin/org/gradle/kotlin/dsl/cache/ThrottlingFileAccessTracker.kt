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

package org.gradle.kotlin.dsl.cache

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import org.gradle.internal.file.FileAccessTracker
import java.io.File


/**
 * Skips marks for files already marked within [intervalMillis].
 *
 * The journal timestamps only feed LRU cleanup against a retention window of days, so re-marking
 * the same files on every build in the daemon is pure journal-write overhead. A racy or evicted
 * skip record merely costs one redundant mark.
 */
internal class ThrottlingFileAccessTracker(
    private val delegate: FileAccessTracker,
    private val intervalMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) : FileAccessTracker {

    private val lastMarked: Cache<File, Long> =
        CacheBuilder.newBuilder().maximumSize(MAX_TRACKED_FILES).build()

    override fun markAccessed(file: File) {
        val now = clock()
        val last = lastMarked.getIfPresent(file)
        if (last == null || now - last >= intervalMillis) {
            lastMarked.put(file, now)
            delegate.markAccessed(file)
        }
    }
}


private const val MAX_TRACKED_FILES = 10_000L
