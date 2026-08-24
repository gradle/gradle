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

package org.gradle.test.fixtures.server.http

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * Regression coverage for the HTTP fixture serving files by streaming rather than
 * buffering them entirely in memory.
 *
 * The served file is larger than the maximum size of a Java array ({@code Integer.MAX_VALUE}
 * bytes, ~2 GiB). Reading such a file into a single {@code byte[]} (as the buffering path did
 * when {@code EtagStrategy.NONE} incorrectly selected it) fails with an {@link OutOfMemoryError}
 * no matter how large the heap is, so this test reproduces the failure deterministically without
 * having to constrain the JVM heap. Streaming has no such limit. The file is sparse, so it is
 * created instantly and occupies effectively no disk.
 */
class HttpServerLargeFileStreamingTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def server = new HttpServer()

    def setup() {
        server.start()
    }

    def cleanup() {
        server.stop()
    }

    def "serves a file larger than the maximum Java array size without buffering it in memory"() {
        given:
        long size = 2L * 1024 * 1024 * 1024 + 1 // just over Integer.MAX_VALUE, so it cannot fit in a byte[]
        def file = tmpDir.file("large.bin")
        createSparseFile(file, size)

        // Chunked transfer avoids the fixture's 32-bit Content-Length header, which cannot
        // represent a response body larger than 2 GiB.
        server.chunkedTransfer = true
        server.expectGet("/large.bin", file)

        when:
        long received = 0
        new URL("${server.uri}/large.bin").openStream().withCloseable { input ->
            byte[] buffer = new byte[1024 * 1024]
            int read
            while ((read = input.read(buffer)) != -1) {
                received += read
            }
        }

        then:
        received == size
    }

    private static void createSparseFile(File file, long size) {
        new RandomAccessFile(file, "rw").withCloseable { it.setLength(size) }
    }
}
