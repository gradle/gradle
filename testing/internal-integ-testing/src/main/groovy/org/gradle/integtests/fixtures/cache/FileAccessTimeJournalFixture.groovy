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

package org.gradle.integtests.fixtures.cache

import org.gradle.cache.internal.btree.BTreePersistentIndexedCache
import org.gradle.test.fixtures.file.TestFile

import static java.util.concurrent.TimeUnit.DAYS
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.CACHE_KEY
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.FILE_ACCESS_CACHE_NAME
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.FILE_ACCESS_PROPERTIES_FILE_NAME
import static org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal.INCEPTION_TIMESTAMP_KEY
import static org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import static org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER

trait FileAccessTimeJournalFixture extends CachingIntegrationFixture {
    TestFile getJournal() {
        userHomeCacheDir.file(CACHE_KEY, FILE_ACCESS_CACHE_NAME + ".bin")
    }

    TestFile getFileAccessPropertiesFile() {
        userHomeCacheDir.file(CACHE_KEY, FILE_ACCESS_PROPERTIES_FILE_NAME)
    }

    void writeLastFileAccessTimeToJournal(File file, long millis) {
        def cache = new BTreePersistentIndexedCache<File, Long>(journal, FILE_SERIALIZER, LONG_SERIALIZER)
        try {
            cache.put(file, millis)
        } finally {
            cache.close()
        }
    }

    void writeJournalInceptionTimestamp(long millis) {
        fileAccessPropertiesFile.text = "${INCEPTION_TIMESTAMP_KEY} = $millis"
    }

    long daysAgo(long days) {
        System.currentTimeMillis() - DAYS.toMillis(days)
    }
}
