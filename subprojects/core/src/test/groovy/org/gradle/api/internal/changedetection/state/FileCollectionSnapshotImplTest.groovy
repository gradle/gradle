/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import com.google.common.hash.HashCode
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder
import spock.lang.Specification

class FileCollectionSnapshotImplTest extends Specification {
    def "order-insensitive collection snapshot ignores order when hashing"() {
        def builder = Mock(TaskCacheKeyBuilder)
        def oldSnapshot = new FileCollectionSnapshotImpl([
            "file1.txt": new FileHashSnapshot(HashCode.fromInt(123)),
            "file2.txt": new FileHashSnapshot(HashCode.fromInt(234)),
        ], false)
        def newSnapshot = new FileCollectionSnapshotImpl([
            "file2.txt": new FileHashSnapshot(HashCode.fromInt(234)),
            "file1.txt": new FileHashSnapshot(HashCode.fromInt(123)),
        ], false)
        when:
        oldSnapshot.appendToCacheKey(builder)
        then:
        1 * builder.putString("file1.txt")
        1 * builder.putHashCode(HashCode.fromInt(123))
        1 * builder.putString("file2.txt")
        1 * builder.putHashCode(HashCode.fromInt(234))
        0 * _

        when:
        newSnapshot.appendToCacheKey(builder)
        then:
        1 * builder.putString("file1.txt")
        1 * builder.putHashCode(HashCode.fromInt(123))
        1 * builder.putString("file2.txt")
        1 * builder.putHashCode(HashCode.fromInt(234))
        0 * _
    }

    def "order-sensitive collection snapshot considers order when hashing"() {
        def builder = Mock(TaskCacheKeyBuilder)
        def oldSnapshot = new FileCollectionSnapshotImpl([
            "file1.txt": new FileHashSnapshot(HashCode.fromInt(123)),
            "file2.txt": new FileHashSnapshot(HashCode.fromInt(234)),
        ], true)
        def newSnapshot = new FileCollectionSnapshotImpl([
            "file2.txt": new FileHashSnapshot(HashCode.fromInt(234)),
            "file1.txt": new FileHashSnapshot(HashCode.fromInt(123)),
        ], true)
        when:
        oldSnapshot.appendToCacheKey(builder)
        then:
        1 * builder.putString("file1.txt")
        1 * builder.putBytes(HashCode.fromInt(123).asBytes())
        1 * builder.putString("file2.txt")
        1 * builder.putBytes(HashCode.fromInt(234).asBytes())
        0 * _

        when:
        newSnapshot.appendToCacheKey(builder)
        then:
        1 * builder.putString("file2.txt")
        1 * builder.putBytes(HashCode.fromInt(234).asBytes())
        1 * builder.putString("file1.txt")
        1 * builder.putBytes(HashCode.fromInt(123).asBytes())
        0 * _
    }
}
