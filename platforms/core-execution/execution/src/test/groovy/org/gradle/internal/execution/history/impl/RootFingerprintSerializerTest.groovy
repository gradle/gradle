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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.RootFingerprint
import org.gradle.internal.fingerprint.impl.DefaultFileSystemLocationFingerprint
import org.gradle.internal.fingerprint.impl.IgnoredPathFileSystemLocationFingerprint
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.SerializerSpec

class RootFingerprintSerializerTest extends SerializerSpec {
    def serializer = new RootFingerprintSerializer(new StringInterner())

    def "reads and writes root fingerprints"() {
        def root = new RootFingerprint("/root", TestHashCodes.hashCodeFrom(1), ImmutableMap.of(
            "/root", new DefaultFileSystemLocationFingerprint("", FileType.Directory, FileSystemLocationFingerprint.DIR_SIGNATURE),
            "/root/a/b.txt", new DefaultFileSystemLocationFingerprint("a/b.txt", FileType.RegularFile, TestHashCodes.hashCodeFrom(2)),
            "/root/missing", new DefaultFileSystemLocationFingerprint("missing", FileType.Missing, FileSystemLocationFingerprint.MISSING_FILE_SIGNATURE),
            "/elsewhere/c.txt", IgnoredPathFileSystemLocationFingerprint.create(FileType.RegularFile, TestHashCodes.hashCodeFrom(3))
        ))

        when:
        def out = serialize(root, serializer)

        then:
        out.rootPath == "/root"
        out.rootHash == TestHashCodes.hashCodeFrom(1)
        out.fingerprints.keySet() as List == ["/root", "/root/a/b.txt", "/root/missing", "/elsewhere/c.txt"]
        out.fingerprints["/root"].type == FileType.Directory
        out.fingerprints["/root/a/b.txt"].with {
            type == FileType.RegularFile
            normalizedPath == "a/b.txt"
            normalizedContentHash == TestHashCodes.hashCodeFrom(2)
        }
        out.fingerprints["/root/missing"].type == FileType.Missing
        out.fingerprints["/elsewhere/c.txt"].with {
            type == FileType.RegularFile
            normalizedPath == ""
            normalizedContentHash == TestHashCodes.hashCodeFrom(3)
        }
    }

    def "reads and writes root fingerprint without entries"() {
        def root = new RootFingerprint("/missing", TestHashCodes.hashCodeFrom(1), ImmutableMap.of())

        when:
        def out = serialize(root, serializer)

        then:
        out.rootPath == "/missing"
        out.fingerprints.isEmpty()
    }
}
