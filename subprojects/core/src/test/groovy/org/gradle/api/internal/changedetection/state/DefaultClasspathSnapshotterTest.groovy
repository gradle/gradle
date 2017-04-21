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

import org.gradle.api.internal.changedetection.resources.recorders.SnapshottingResultRecorder
import org.gradle.api.internal.changedetection.snapshotting.DefaultSnapshottingConfiguration
import org.gradle.api.snapshotting.ClasspathEntry
import org.gradle.internal.reflect.DirectInstantiator
import spock.lang.Subject

@Subject(DefaultClasspathSnapshotter)
class DefaultClasspathSnapshotterTest extends AbstractSnapshotterTest {
    List entryHashes = []

    def setup() {
        snapshotter = new DefaultClasspathSnapshotter(
            fileSystemSnapshotter,
            new ValueSnapshotter(null),
            stringInterner,
            store,
            new DefaultSnapshottingConfiguration([ClasspathEntry], DirectInstantiator.INSTANCE)
        ) {
            @Override
            protected FileCollectionSnapshotBuilder createFileCollectionSnapshotBuilder(SnapshotNormalizationStrategy normalizationStrategy, TaskFilePropertyCompareStrategy compareStrategy) {
                return new ReportingFileCollectionSnapshotBuilder(super.createFileCollectionSnapshotBuilder(normalizationStrategy, compareStrategy), snapshots)
            }

            @Override
            SnapshottingResultRecorder create() {
                def jarContents = [:]
                entryHashes.add(jarContents)
                return new ReportingSnapshottingResultRecorder(null, super.create(), jarContents)
            }
        }
    }

    def "root elements are unsorted, non-root elements are sorted amongst themselves"() {
        def rootFile1 = tmpDir.file("root1.txt") << "root1"
        def rootDir = tmpDir.file("dir").createDir()
        rootDir.file("file1.txt") << "file1"
        rootDir.file("file2.txt") << "file2"
        def rootFile2 = tmpDir.file("root2.txt") << "root2"

        when:
        def (hash, snapshots, fileCollectionSnapshot) = snapshot(rootFile1, rootDir, rootFile2)
        then:
        hash == 'c27b1bdb349f6f8b9fe70a6a1b05d400'
        def expectedEntrySnapshots = [
            'dir': [
                'file1.txt': '60c913683cc577eae172594b76316d06',
                'file2.txt': 'e0d9760b191a5dc21838e8a16f956bb0',
                hash: '9d47e46923d97f1938e7689be6cef03a'
            ],
            'root2.txt': 'd41d8cd98f00b204e9800998ecf8427e',
            'root1.txt': 'd41d8cd98f00b204e9800998ecf8427e',
        ]
        snapshots == expectedEntrySnapshots
        fileCollectionSnapshot == [
            ['root1.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['dir', '', 'DIR'],
            ['file1.txt', 'file1.txt', '60c913683cc577eae172594b76316d06'],
            ['file2.txt', 'file2.txt', 'e0d9760b191a5dc21838e8a16f956bb0'],
            ['root2.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
        ]

        when:
        jarCache.allEntries.clear()
        (hash, snapshots, fileCollectionSnapshot) = snapshot(rootFile2, rootFile1, rootDir)
        then:
        hash == '14b6545b017e1edb1734aee0f1d8e058'
        snapshots == expectedEntrySnapshots
        fileCollectionSnapshot == [
            ['root2.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['root1.txt', '', 'd41d8cd98f00b204e9800998ecf8427e'],
            ['dir', '', 'DIR'],
            ['file1.txt', 'file1.txt', '60c913683cc577eae172594b76316d06'],
            ['file2.txt', 'file2.txt', 'e0d9760b191a5dc21838e8a16f956bb0'],
        ]
    }

    def "snapshots runtime classpath files"() {
        def zipFile = file('library.jar')
        file('zipContents').create {
            file('firstFile.txt').text = "Some text"
            file('secondFile.txt').text = "Second File"
            subdir {
                file('someOtherFile.log').text = "File in subdir"
            }
        }.zipTo(zipFile)
        def classes = file('classes').create {
            file('thirdFile.txt').text = "Third file"
            file('fourthFile.txt').text = "Fourth file"
            subdir {
                file('build.log').text = "File in subdir"
            }
        }

        when:
        def (hash, snapshots, fileCollectionSnapshot) = snapshot(zipFile, classes)
        then:
        hash == '8d79d677c7cba1841912a5e15ab847c8'

        fileCollectionSnapshot == [
            ['library.jar', '', 'f31495fd1bb4b8c3b8fb1f46a68adf9e'],
            ['classes', '', 'DIR'],
            ['fourthFile.txt', 'fourthFile.txt', '8fd6978401143ae9adc277e9ce819f7e'],
            ['build.log', 'subdir/build.log', 'abf951c0fe2b682313add34f016bcb30'],
            ['thirdFile.txt', 'thirdFile.txt', '728271a3405e112740bfd3198cfa70de'],
        ]

        snapshots == [
            classes: [
                'fourthFile.txt': '8fd6978401143ae9adc277e9ce819f7e',
                'subdir/build.log': 'abf951c0fe2b682313add34f016bcb30',
                'thirdFile.txt': '728271a3405e112740bfd3198cfa70de',
                hash: 'fa5654d3c632f8b6e29ecaee439a5f15',
            ],
            'library.jar': 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
        ]

        entryHashes as Set == [
            [
                'firstFile.txt': '9db5682a4d778ca2cb79580bdb67083f',
                'secondFile.txt': '82e72efeddfca85ddb625e88af3fe973',
                'subdir/someOtherFile.log': 'a9cca315f4b8650dccfa3d93284998ef',
                hash: 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
            ],
            [
                'fourthFile.txt': '8fd6978401143ae9adc277e9ce819f7e',
                'subdir/build.log': 'abf951c0fe2b682313add34f016bcb30',
                'thirdFile.txt': '728271a3405e112740bfd3198cfa70de',
                hash: 'fa5654d3c632f8b6e29ecaee439a5f15',
            ]
        ] as Set

        jarCache.allEntries.size() == 1
        def key = jarCache.allEntries.keySet().iterator().next()
        jarCache.get(key).toString() == 'f31495fd1bb4b8c3b8fb1f46a68adf9e'
    }

    def "cache hashes for jar files"() {
        def zipFile = file('library.jar')
        file('zipContents').create {
            file('firstFile.txt').text = "Some text"
            file('secondFile.txt').text = "Second File"
            subdir {
                file('someOtherFile.log').text = "File in subdir"
            }
        }.zipTo(zipFile)

        def zipFile2 = file('another-library.jar')
        file('anotherZipContents').create {
            file('thirdFile.txt').text = "third file"
            file('forthFile.txt').text = "forth file"
            subdir {
                file('someEvenOtherFile.log').text = "another file in subdir"
            }
        }.zipTo(zipFile2)

        when:
        def (hash, snapshots) = snapshot(zipFile, zipFile2)

        then:
        snapshots == [
                'library.jar': 'f31495fd1bb4b8c3b8fb1f46a68adf9e',
                'another-library.jar': '4c54ecab47d005e6862ced54627c6208'
        ]
        hash == '3c034ccdc7dc2fd2a5e84b573a772f1c'
        entryHashes.size() == 2
        jarCache.allEntries.size() == 2
        def values = jarCache.allEntries.keySet().collect { jarCache.get(it).toString() } as Set
        values == ['f31495fd1bb4b8c3b8fb1f46a68adf9e', '4c54ecab47d005e6862ced54627c6208'] as Set

        when:
        entryHashes.clear()
        (hash, snapshots) = snapshot(zipFile, zipFile2)
        values = jarCache.allEntries.keySet().collect { jarCache.get(it).toString() } as Set

        then:
        snapshots == [
            'library.jar': 'f31495fd1bb4b8c3b8fb1f46a68adf9e',
            'another-library.jar': '4c54ecab47d005e6862ced54627c6208'
        ]
        hash == '3c034ccdc7dc2fd2a5e84b573a772f1c'
        entryHashes.empty
        jarCache.allEntries.size() == 2
        values == ['f31495fd1bb4b8c3b8fb1f46a68adf9e', '4c54ecab47d005e6862ced54627c6208'] as Set
    }

}
