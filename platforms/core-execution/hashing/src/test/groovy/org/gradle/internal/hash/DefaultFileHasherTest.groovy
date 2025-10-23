/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.hash

import org.gradle.util.internal.TextUtil
import spock.lang.Specification

final class DefaultFileHasherTest extends Specification {
    private tempDir = File.createTempDir()
    private hasher = new DefaultFileHasher(new DefaultStreamHasher())

    def "can't hash non-existent file"() {
        given:
        def file = new File(tempDir, "doesnt-exist.txt")

        when:
        hasher.hash(file)

        then:
        def e = thrown(UncheckedIOException)
        e.message == "Failed to create MD5 hash for file: ${TextUtil.normaliseFileSeparators(file.absolutePath)} (No such file or directory)"
    }

    def "can't hash unreadable file"() {
        given:
        def file = new File(tempDir, "unreadable.txt")
        file.createNewFile()
        file.setReadable(false)

        when:
        hasher.hash(file)

        then:
        def e = thrown(UncheckedIOException)
        e.message == "Failed to create MD5 hash for file: ${TextUtil.normaliseFileSeparators(file.absolutePath)} (Permission denied)"
    }
}
