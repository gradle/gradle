/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class FileToArchiveEntrySetTransformerTest extends Specification {

    @Rule TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider()
    Set<ArchiveEntry> entrySet

    def transformer = new FileToArchiveEntrySetTransformer(new ZipEntryToArchiveEntryTransformer())

    TestFile contents
    TestFile zip

    def setup() {
        contents = dir.createDir("contents")
        zip = dir.file("zip.zip")
    }

    def createZip(Closure c) {
        contents.with(c)
        contents.zipTo(zip)
        entrySet = transformer.transform(zip)
    }

    def "can handle zip file"() {
        when:
        createZip {
            createFile("f1.txt") << "f1"
            createFile("dir1/f2.txt") << "f2"
            createFile("dir1/f3.txt") << "f3"
            createFile("dir2/f4.txt") << "f3"
        }

        then:
        entrySet.size() == 6
        entrySet*.path.sort() == ["dir1/", "dir1/f2.txt", "dir1/f3.txt", "dir2/", "dir2/f4.txt", "f1.txt"]
    }

    def "can handle empty zip"() {
        when:
        createZip { }

        then:
        entrySet.empty
    }
}
