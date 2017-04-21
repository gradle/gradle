/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files

class DefaultCompileClasspathSnapshotterTest extends AbstractSnapshotterTest {
    private static final String JAR_FILE_HASH = '2795ef480ca940f05aad4c08c2510346'
    public static final String IGNORED_SIGNATURE = 'd9866ca2e5b4d2d882a57251c5e35f4a'

    List entryHashes = []

    def setup() {
        snapshotter = new DefaultCompileClasspathSnapshotter(
            fileSystemSnapshotter,
            stringInterner,
            store
        ) {
            private ReportingFileCollectionSnapshotBuilder reportingFileCollectionSnapshotBuilder

            @Override
            protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
                reportingFileCollectionSnapshotBuilder = new ReportingFileCollectionSnapshotBuilder(super.createFileCollectionSnapshotBuilder(normalizationStrategy, compareStrategy), snapshots)
                return reportingFileCollectionSnapshotBuilder
            }

            @Override
            SnapshottingResultRecorder create() {
                def jarContents = [:]
                entryHashes.add(jarContents)
                return new ReportingSnapshottingResultRecorder(null, super.create(), jarContents)
            }
        }
    }

    def "ignores non-class files"() {
        def rootFile1 = tmpDir.file("root1.jar") << "root1"
        def rootDir = tmpDir.file("dir").createDir()
        rootDir.file("file1.class") << "file1"
        rootDir.file("file2.txt") << "file2"
        def rootFile2 = tmpDir.file("root2.other-root-file") << "root2"
        def zipFile = createJarFile(file('library.jar'))

        when:
        def (hash, snapshots, fileCollectionSnapshot) = snapshot(rootFile1, rootDir, rootFile2, zipFile)
        then:
        snapshots == [
            'root1.jar': 'd41d8cd98f00b204e9800998ecf8427e',
            'dir': [
                'file1.class': '826e8142e6baabe8af779f5f490cf5f5',
                'hash': '3063e74bd8d52219df6cc61dab5c51cd',
            ],
            'root2.other-root-file': 'd41d8cd98f00b204e9800998ecf8427e',
            'library.jar': JAR_FILE_HASH
        ]
        entryHashes as Set == [
            [hash: 'd41d8cd98f00b204e9800998ecf8427e'],
            ['file1.class': '826e8142e6baabe8af779f5f490cf5f5', hash: '3063e74bd8d52219df6cc61dab5c51cd'],
            ['FirstClass.class': '7631ebee7806788b6a4b762baa86faf0', 'subpackage/SomeOtherClass.class': 'f27ec3cd67613786a02a2ee4fc622567', hash: JAR_FILE_HASH]
        ] as Set
        hash == '98662b9287e007cc283a8d14d0c5ffc5'
        fileCollectionSnapshot == [
            ['root1.jar', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['dir', '', 'DIR'],
            ['file1.class', 'file1.class', '826e8142e6baabe8af779f5f490cf5f5'],
            ['root2.other-root-file', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['library.jar', '', JAR_FILE_HASH]
        ]
    }

    def "caches jar hashes"() {
        def jarFile = createJarFile(file('library.jar'))

        when:
        snapshot(jarFile)

        then:
        entryHashes.size() == 1
        jarCache.allEntries.size() == 1
        def key = jarCache.allEntries.keySet().iterator().next()
        jarCache.get(key).toString() == JAR_FILE_HASH

        when:
        entryHashes.clear()
        snapshot(jarFile)

        then:
        entryHashes.empty
        jarCache.allEntries.size() == 1
        jarCache.get(key).toString() == JAR_FILE_HASH
    }

    def "caches class file hashes"() {
        def rootDir = tmpDir.file("dir").createDir()
        rootDir.file("FirstClass.class").writeClassFile()
        rootDir.file('FirstClass$InnerClass.class').writeInnerClass()
        rootDir.file("SecondClass.class") << "not a class file"

        when:
        snapshot(rootDir)

        then:
        entryHashes.size() == 1
        jarCache.allEntries.size() == 3
        allCachedValues == ['7631ebee7806788b6a4b762baa86faf0', '39d6f939a32937f26e63bde2949e8d30', IGNORED_SIGNATURE] as Set

        when:
        entryHashes.clear()
        snapshot(rootDir)

        then:
        entryHashes.size() == 1
        jarCache.allEntries.size() == 3
        allCachedValues == ['7631ebee7806788b6a4b762baa86faf0', '39d6f939a32937f26e63bde2949e8d30', IGNORED_SIGNATURE] as Set
    }

    private Set<String> getAllCachedValues() {
        jarCache.allEntries.keySet().collect { jarCache.get(it).toString() } as Set
    }

    private TestFile createJarFile(TestFile zipFile) {
        def contents = new TestFile(Files.createTempDirectory(tmpDir.testDirectory.toPath(), "zipContents").toFile())
        contents.create {
            file('FirstClass.class').writeClassFile()
            file('secondFile.txt').text = "Second File"
            subpackage {
                file('SomeOtherClass.class').writeClassFile("subpackage")
            }
        }.zipTo(zipFile)
        return zipFile
    }
}
