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

package org.gradle.internal.fingerprint.classpath.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService
import org.gradle.api.internal.changedetection.state.ResourceFilter
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryIndexedCache
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tmpDir")
@UsesNativeServices
class DefaultClasspathFingerprinterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystem = TestFiles.fileSystem()
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def fileHasher = new TestFileHasher()
    def fileSystemSnapshotter = new DefaultFileSystemSnapshotter(fileHasher, stringInterner, fileSystem, fileSystemMirror)
    InMemoryIndexedCache<HashCode, HashCode> resourceHashesCache = new InMemoryIndexedCache<>(new HashCodeSerializer())
    def cacheService = new DefaultResourceSnapshotterCacheService(resourceHashesCache)
    def fingerprinter = new DefaultClasspathFingerprinter(
        cacheService,
        fileSystemSnapshotter,
        ResourceFilter.FILTER_NOTHING,
        stringInterner)

    def "directories and missing files are ignored"() {
        def emptyDir = file('root/emptyDir').createDir()
        def missingFile = file('some').createDir().file('does-not-exist')
        def missingRootFile = file('missing-root')

        when:
        def fileCollectionFingerprint = fingerprint(emptyDir.parentFile, missingFile.parentFile, missingRootFile)

        then:
        fileCollectionFingerprint.empty
    }

    def "root elements are unsorted, non-root elements are sorted amongst themselves"() {
        def rootFile1 = file("root1.txt") << "root1"
        def rootDir = file("dir").createDir()
        rootDir.file("file1.txt") << "file1"
        rootDir.file("file2.txt") << "file2"
        def rootFile2 = file("root2.txt") << "root2"

        when:
        def fileCollectionFingerprint = fingerprint(rootFile1, rootDir, rootFile2)

        then:
        fileCollectionFingerprint == [
            ['root1.txt', '', 'e5d9dee0892c9f474a174d3bfffb7810'],
            ['file1.txt', 'file1.txt', '826e8142e6baabe8af779f5f490cf5f5'],
            ['file2.txt', 'file2.txt', '1c1c96fd2cf8330db0bfa936ce82f3b9'],
            ['root2.txt', '', '9b70d6dbfb1457d05e4e2c2fbb42d7db'],
        ]

        when:
        fileCollectionFingerprint = fingerprint(rootFile2, rootFile1, rootDir)
        then:
        fileCollectionFingerprint == [
            ['root2.txt', '', '9b70d6dbfb1457d05e4e2c2fbb42d7db'],
            ['root1.txt', '', 'e5d9dee0892c9f474a174d3bfffb7810'],
            ['file1.txt', 'file1.txt', '826e8142e6baabe8af779f5f490cf5f5'],
            ['file2.txt', 'file2.txt', '1c1c96fd2cf8330db0bfa936ce82f3b9'],
        ]
    }

    def "fingerprints runtime classpath files"() {
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
        def fileCollectionFingerprint = fingerprint(zipFile, classes)
        then:

        fileCollectionFingerprint == [
            ['library.jar', '', '397fdb436f96f0ebac6c1e147eb1cc51'],
            ['fourthFile.txt', 'fourthFile.txt', '6c99cb370b82c9c527320b35524213e6'],
            ['build.log', 'subdir/build.log', 'a9cca315f4b8650dccfa3d93284998ef'],
            ['thirdFile.txt', 'thirdFile.txt', '3f1d3e7fb9620156f8e911fb90d89c42'],
        ]

        resourceHashesCache.keySet().size() == 1
        def key = resourceHashesCache.keySet().iterator().next()
        resourceHashesCache.get(key).toString() == '397fdb436f96f0ebac6c1e147eb1cc51'
    }

    def "detects moving of files in jars and directories"() {
        def zipFile = file('library.jar')
        file('zipContents').create {
            file('firstFile.txt').text = "Some text"
            subdir {}
        }.zipTo(zipFile)
        def classes = file('classes').create {
            file('thirdFile.txt').text = "Third file"
            subdir {}
        }

        when:
        def fileCollectionFingerprint = fingerprint(zipFile, classes)
        then:
        fileCollectionFingerprint == [
            ['library.jar', '', '23f0ce5817e3d0db8abc266027e0793d'],
            ['thirdFile.txt', 'thirdFile.txt', '3f1d3e7fb9620156f8e911fb90d89c42'],
        ]

        when:
        file('zipContents/firstFile.txt').moveToDirectory(file('zipContents/subdir'))
        file('classes/thirdFile.txt').moveToDirectory(file('classes/subdir'))
        file('zipContents').zipTo(zipFile)

        fileCollectionFingerprint = fingerprint(zipFile, classes)

        then:
        fileCollectionFingerprint == [
            ['library.jar', '', '78a21c0d0074d113b7446695184fb8e0'],
            ['thirdFile.txt', 'subdir/thirdFile.txt', '3f1d3e7fb9620156f8e911fb90d89c42'],
        ]
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
        def fileCollectionFingerprint = fingerprint(zipFile, zipFile2)

        then:
        fileCollectionFingerprint == [
            ['library.jar', '', '397fdb436f96f0ebac6c1e147eb1cc51'],
            ['another-library.jar', '', 'e9fa562dd3fd73bfa315b0f9876c2b6e']
        ]
        resourceHashesCache.keySet().size() == 2
        def values = resourceHashesCache.keySet().collect { resourceHashesCache.get(it).toString() } as Set
        values == ['397fdb436f96f0ebac6c1e147eb1cc51', 'e9fa562dd3fd73bfa315b0f9876c2b6e'] as Set

        when:
        fileCollectionFingerprint = fingerprint(zipFile, zipFile2)
        values = resourceHashesCache.keySet().collect { resourceHashesCache.get(it).toString() } as Set

        then:
        fileCollectionFingerprint == [
            ['library.jar', '', '397fdb436f96f0ebac6c1e147eb1cc51'],
            ['another-library.jar', '', 'e9fa562dd3fd73bfa315b0f9876c2b6e']
        ]
        resourceHashesCache.keySet().size() == 2
        values == ['397fdb436f96f0ebac6c1e147eb1cc51', 'e9fa562dd3fd73bfa315b0f9876c2b6e'] as Set
    }

    def fingerprint(TestFile... classpath) {
        fileSystemMirror.beforeOutputChange()
        def fileCollectionFingerprint = fingerprinter.fingerprint(files(classpath))
        return fileCollectionFingerprint.fingerprints.collect { String path, FileSystemLocationFingerprint fingerprint ->
            [new File(path).getName(), fingerprint.normalizedPath, fingerprint.normalizedContentHash.toString()]
        }
    }

    def files(File... files) {
        return ImmutableFileCollection.of(files)
    }

    def file(Object... path) {
        tmpDir.file(path)
    }
}
