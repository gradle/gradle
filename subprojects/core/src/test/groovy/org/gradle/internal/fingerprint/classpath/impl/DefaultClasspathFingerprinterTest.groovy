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
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter
import org.gradle.api.internal.changedetection.state.ResourceFilter
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.TestInMemoryIndexedCache
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory(fieldName = "tmpDir")
@UsesNativeServices
class DefaultClasspathFingerprinterTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def stringInterner = Stub(StringInterner) {
        intern(_) >> { String s -> s }
    }
    def fileSystemAccess = TestFiles.fileSystemAccess()
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.fileSystem())
    TestInMemoryIndexedCache<HashCode, HashCode> resourceHashesCache = new TestInMemoryIndexedCache<>(new HashCodeSerializer())
    def cacheService = new DefaultResourceSnapshotterCacheService(resourceHashesCache)
    def fingerprinter = new DefaultClasspathFingerprinter(
        cacheService,
        fileCollectionSnapshotter,
        ResourceFilter.FILTER_NOTHING,
        ResourceEntryFilter.FILTER_NOTHING,
        PropertiesFileFilter.FILTER_NOTHING,
        stringInterner,
        LineEndingSensitivity.DEFAULT
    )

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
            ['root1.txt', '', '006240e2be8cab1da7ef856d241a35e0'],
            ['file1.txt', 'file1.txt', '747a88be8259e66d39362bcc204e3276'],
            ['file2.txt', 'file2.txt', '7e9123cc118f9c4f436614b9f9402e13'],
            ['root2.txt', '', '2b86399532976c2fb33f09bc7bdee422'],
        ]

        when:
        fileCollectionFingerprint = fingerprint(rootFile2, rootFile1, rootDir)
        then:
        fileCollectionFingerprint == [
            ['root2.txt', '', '2b86399532976c2fb33f09bc7bdee422'],
            ['root1.txt', '', '006240e2be8cab1da7ef856d241a35e0'],
            ['file1.txt', 'file1.txt', '747a88be8259e66d39362bcc204e3276'],
            ['file2.txt', 'file2.txt', '7e9123cc118f9c4f436614b9f9402e13'],
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
            ['library.jar', '', 'e3f8c5a79d138a40570261fa7de40642'],
            ['fourthFile.txt', 'fourthFile.txt', '37b040e234b12a70145edbdb79683ee9'],
            ['build.log', 'subdir/build.log', '224c45bdc38a7e3c52cdcc6126d78946'],
            ['thirdFile.txt', 'thirdFile.txt', '138f1960a77eecec5f03362421bf967a'],
        ]

        resourceHashesCache.keySet().size() == 1
        def key = resourceHashesCache.keySet().iterator().next()
        resourceHashesCache.getIfPresent(key).toString() == 'e3f8c5a79d138a40570261fa7de40642'
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
            ['library.jar', '', 'eaafc4a09214a09ffe2de2d02d5abd3c'],
            ['thirdFile.txt', 'thirdFile.txt', '138f1960a77eecec5f03362421bf967a'],
        ]

        when:
        file('zipContents/firstFile.txt').moveToDirectory(file('zipContents/subdir'))
        file('classes/thirdFile.txt').moveToDirectory(file('classes/subdir'))
        file('zipContents').zipTo(zipFile)

        fileCollectionFingerprint = fingerprint(zipFile, classes)

        then:
        fileCollectionFingerprint == [
            ['library.jar', '', 'c23694a1b7e494fab2aa21f5d644d0bc'],
            ['thirdFile.txt', 'subdir/thirdFile.txt', '138f1960a77eecec5f03362421bf967a'],
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
            ['library.jar', '', 'e3f8c5a79d138a40570261fa7de40642'],
            ['another-library.jar', '', '5d9e97561e1c86c891600c8df838f7de']
        ]
        resourceHashesCache.keySet().size() == 2
        def values = resourceHashesCache.keySet().collect { resourceHashesCache.getIfPresent(it).toString() } as Set
        values == ['e3f8c5a79d138a40570261fa7de40642', '5d9e97561e1c86c891600c8df838f7de'] as Set

        when:
        fileCollectionFingerprint = fingerprint(zipFile, zipFile2)
        values = resourceHashesCache.keySet().collect { resourceHashesCache.getIfPresent(it).toString() } as Set

        then:
        fileCollectionFingerprint == [
            ['library.jar', '', 'e3f8c5a79d138a40570261fa7de40642'],
            ['another-library.jar', '', '5d9e97561e1c86c891600c8df838f7de']
        ]
        resourceHashesCache.keySet().size() == 2
        values == ['e3f8c5a79d138a40570261fa7de40642', '5d9e97561e1c86c891600c8df838f7de'] as Set
    }

    def "empty jars are not ignored"() {
        def emptyJar = file('empty.jar')
        file('emptyDir').createDir().zipTo(emptyJar)
        def nonEmptyJar = file('nonEmpty.jar')
        file('nonEmptyDir').create{
            file('some-resource').text = 'not-empty'
        }.zipTo(nonEmptyJar)

        when:
        def classpathFingerprint = fingerprint(emptyJar, nonEmptyJar)
        then:
        classpathFingerprint == [
            ['empty.jar', '', '73aaa2573075495cce6048c54637763c'],
            ['nonEmpty.jar', '', '252a9e49dedde612c99dd76008d11b03']
        ]
    }

    def fingerprint(TestFile... classpath) {
        fileSystemAccess.write(classpath.collect { it.absolutePath }, {})
        def fileCollectionFingerprint = fingerprinter.fingerprint(files(classpath))
        return fileCollectionFingerprint.fingerprints.collect { String path, FileSystemLocationFingerprint fingerprint ->
            [new File(path).getName(), fingerprint.normalizedPath, fingerprint.normalizedContentHash.toString()]
        }
    }

    def files(File... files) {
        return TestFiles.fixed(files)
    }

    def file(Object... path) {
        tmpDir.file(path)
    }
}
