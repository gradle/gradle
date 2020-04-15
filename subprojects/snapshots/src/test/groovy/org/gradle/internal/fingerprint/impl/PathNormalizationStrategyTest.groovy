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

package org.gradle.internal.fingerprint.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.MutableReference
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class PathNormalizationStrategyTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    private StringInterner stringInterner = new StringInterner()

    public static final String IGNORED = "IGNORED"
    def virtualFileSystem = TestFiles.virtualFileSystem()

    List<FileSystemSnapshot> roots
    TestFile jarFile1
    TestFile jarFile2
    TestFile resources
    String subDirA = "a"
    String subDirB = "b"
    String fileInRoot = "input.txt"
    String fileInSubdirA = "a/input-1.txt"
    String fileInSubdirB = "b/input-2.txt"
    TestFile emptyDir
    TestFile missingFile

    def setup() {
        jarFile1 = file("dir/libs/library-a.jar")
        jarFile1 << "JAR file #1"
        jarFile2 = file("dir/libs/library-b.jar")
        jarFile2 << "JAR file #2"
        resources = file("dir/resources")
        resources.file("input.txt") << "main input"
        resources.file("a/input-1.txt") << "input #1"
        resources.file("b/input-2.txt") << "input #2"
        emptyDir = file("empty-dir")
        emptyDir.mkdirs()
        missingFile = file("missing-file")

        roots = [
            snapshot(jarFile1),
            snapshot(jarFile2),
            snapshot(resources),
            snapshot(emptyDir),
            snapshot(missingFile)
        ]
    }

    private CompleteFileSystemLocationSnapshot snapshot(File file) {
        MutableReference<CompleteFileSystemLocationSnapshot> result = MutableReference.empty()
        virtualFileSystem.read(file.absolutePath, result.&set)
        return result.get()
    }

    def "sensitivity NONE"() {
        def fingerprints = collectFingerprints(IgnoredPathFingerprintingStrategy.INSTANCE)
        expect:
        allFilesToFingerprint.each { file ->
            if (file.isFile() || !file.exists()) {
                assert fingerprints[file] == IGNORED
            } else {
                assert fingerprints[file] == null
            }
        }
    }

    def "sensitivity NAME_ONLY"() {
        def fingerprints = collectFingerprints(NameOnlyFingerprintingStrategy.INSTANCE)
        expect:
        (allFilesToFingerprint - emptyDir - resources).each { file ->
            assert fingerprints[file] == file.name
        }
        fingerprints[emptyDir] == IGNORED
        fingerprints[resources] == IGNORED
    }

    def "sensitivity RELATIVE"() {
        def fingerprints = collectFingerprints(new RelativePathFingerprintingStrategy(stringInterner))
        expect:
        fingerprints[jarFile1]                      == jarFile1.name
        fingerprints[jarFile2]                      == jarFile2.name
        fingerprints[resources]                     == IGNORED
        fingerprints[resources.file(fileInRoot)]    == fileInRoot
        fingerprints[resources.file(subDirA)]       == subDirA
        fingerprints[resources.file(fileInSubdirA)] == fileInSubdirA
        fingerprints[resources.file(subDirB)]       == subDirB
        fingerprints[resources.file(fileInSubdirB)] == fileInSubdirB
        fingerprints[emptyDir]                      == IGNORED
        fingerprints[missingFile]                   == missingFile.name
    }

    def "sensitivity ABSOLUTE (include missing = true)"() {
        def fingerprints = collectFingerprints(AbsolutePathFingerprintingStrategy.INCLUDE_MISSING)
        expect:
        allFilesToFingerprint.each { file ->
            assert fingerprints[file] == file.absolutePath
        }
        fingerprints.size() == allFilesToFingerprint.size()
    }

    def "sensitivity ABSOLUTE (include missing = false)"() {
        def fingerprints = collectFingerprints(AbsolutePathFingerprintingStrategy.IGNORE_MISSING)
        expect:
        (allFilesToFingerprint - missingFile).each { file ->
            assert fingerprints[file] == file.absolutePath
        }
        fingerprints.size() == allFilesToFingerprint.size() - 1
    }

    List<File> getAllFilesToFingerprint() {
        [jarFile1, jarFile2, resources, emptyDir, missingFile] + [fileInRoot, subDirA, fileInSubdirA, subDirB, fileInSubdirB].collect { resources.file(it) }
    }

    protected TestFile file(String... path) {
        temporaryFolder.file(path)
    }

    protected def collectFingerprints(FingerprintingStrategy strategy) {
        Map<File, String> fingerprints = [:]
        strategy.collectFingerprints(roots).each { path, normalizedFingerprint ->
            String normalizedPath
            if (normalizedFingerprint instanceof IgnoredPathFileSystemLocationFingerprint) {
                normalizedPath = IGNORED
            } else {
                normalizedPath = normalizedFingerprint.normalizedPath
            }
            fingerprints.put(new File(path), normalizedPath)
        }
        return fingerprints
    }
}
