/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file

import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultTemporaryFileProviderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    DefaultTemporaryFileProvider provider

    def setup() {
        provider = new DefaultTemporaryFileProvider({tmpDir.testDirectory} as Factory)
    }

    def "allocates temp file"() {
        expect:
        provider.newTemporaryFile('a', 'b') == tmpDir.file('a', 'b')
    }

    def "can create temp file"() {
        when:
        def file = provider.createTemporaryFile("prefix", "suffix", "foo/bar")

        then:
        correctTempFileCreated(file)
    }

    def "can create multiple temp files with same arguments"() {
        when:
        def file1 = provider.createTemporaryFile("prefix", "suffix", "foo/bar")
        def file2 = provider.createTemporaryFile("prefix", "suffix", "foo/bar")
        def file3 = provider.createTemporaryFile("prefix", "suffix", "foo/bar")

        then:
        correctTempFileCreated(file1)
        correctTempFileCreated(file2)
        correctTempFileCreated(file2)
        file1 != file2
        file2 != file3
    }

    void correctTempFileCreated(File file) {
        assert file.exists()
        assert file.name.startsWith("prefix")
        assert file.name.endsWith("suffix")
        assert file.path.startsWith(new File(tmpDir.testDirectory, "foo/bar").path)
    }
}
