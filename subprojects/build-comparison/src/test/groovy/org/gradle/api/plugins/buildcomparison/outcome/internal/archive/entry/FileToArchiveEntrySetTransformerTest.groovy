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

    @Rule
    TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider()
    Set<ArchiveEntry> entrySet

    def transformer = new FileToArchiveEntrySetTransformer()

    TestFile contents
    TestFile zip
    TestFile subZipContents
    TestFile subZip
    TestFile subSubZipContents
    TestFile subSubZip

    def setup() {
        contents = dir.createDir("contents")
        zip = dir.file("zip.zip")
        subZipContents = dir.createDir("subZipContents")
        subZip = dir.file("contents/sub.zip")
        subSubZipContents = dir.createDir("subSubZipContents")
        subSubZip = dir.file("subZipContents/subSub.zip")
    }

    def createZip(TestFile contents, TestFile zip) {
        createZip(contents, zip) {}
    }

    def createZip(TestFile contents, TestFile zip, @DelegatesTo(TestFile) Closure c) {
        contents.with(c)
        contents.zipTo(zip)
        entrySet = transformer.transform(zip)
    }

    def "can handle zip file"() {
        when:
        createZip(contents, zip) {
            createFile("f1.txt") << "f1"
            createFile("dir1/f2.txt") << "f2"
            createFile("dir1/f3.txt") << "f3"
            createFile("dir2/f4.txt") << "f3"
        }

        then:
        entrySet.size() == 6
        entrySet*.path.sort()*.toString() == ["dir1/", "dir1/f2.txt", "dir1/f3.txt", "dir2/", "dir2/f4.txt", "f1.txt"]
    }

    def "can handle empty zip file"() {
        when:
        createZip(contents, zip)

        then:
        entrySet.empty
    }

    def "can handle recursive zip file"() {
        when:
        createZip(contents, zip) {
            createFile("f1.txt") << "f1"
            createFile("dir1/f2.txt") << "f2"
            createFile("dir1/f3.txt") << "f3"
            createFile("dir2/f4.txt") << "f3"
            createZip(subZipContents, subZip) {
                createFile("g1.txt") << "g1"
                createFile("dir1/g2.txt") << "g2"
                createFile("dir1/g3.txt") << "g3"
                createFile("dir2/g4.txt") << "g3"
                createZip(subSubZipContents, subSubZip) {
                    createFile("h1.txt") << "h1"
                    createFile("dir1/h2.txt") << "h2"
                    createFile("dir1/h3.txt") << "h3"
                    createFile("dir2/h4.txt") << "h3"
                }
            }
        }

        then:
        entrySet.size() == 20
        entrySet*.path.sort()*.toString() == [
                "dir1/",
                "dir1/f2.txt",
                "dir1/f3.txt",
                "dir2/",
                "dir2/f4.txt",
                "f1.txt",
                "sub.zip",
                "sub.zip!/dir1/",
                "sub.zip!/dir1/g2.txt",
                "sub.zip!/dir1/g3.txt",
                "sub.zip!/dir2/",
                "sub.zip!/dir2/g4.txt",
                "sub.zip!/g1.txt",
                "sub.zip!/subSub.zip",
                "sub.zip!/subSub.zip!/dir1/",
                "sub.zip!/subSub.zip!/dir1/h2.txt",
                "sub.zip!/subSub.zip!/dir1/h3.txt",
                "sub.zip!/subSub.zip!/dir2/",
                "sub.zip!/subSub.zip!/dir2/h4.txt",
                "sub.zip!/subSub.zip!/h1.txt"
        ]
    }
}
