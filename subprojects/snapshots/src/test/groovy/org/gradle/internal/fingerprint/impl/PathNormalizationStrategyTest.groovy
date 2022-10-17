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
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
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

    private static final StringInterner STRING_INTERNER = new StringInterner()

    public static final String IGNORED = "IGNORED"
    def fileSystemAccess = TestFiles.fileSystemAccess()

    FileSystemSnapshot roots
    TestFile jarFile1
    TestFile jarFile2
    TestFile resources
    String subDirA = "a"
    String subDirB = "b"
    String fileInRoot = "input.txt"
    String fileInSubdirA = "a/input-1.txt"
    String fileInSubdirB = "b/input-2.txt"
    TestFile emptyRootDir
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
        emptyRootDir = file("empty-dir")
        emptyRootDir.mkdirs()
        missingFile = file("missing-file")

        roots = CompositeFileSystemSnapshot.of([
            snapshot(jarFile1),
            snapshot(jarFile2),
            snapshot(resources),
            snapshot(emptyRootDir),
            snapshot(missingFile)
        ])
    }

    private FileSystemLocationSnapshot snapshot(File file) {
        return fileSystemAccess.read(file.absolutePath)
    }

    def "sensitivity NONE"() {
        def fingerprints = collectFingerprints(IgnoredPathFingerprintingStrategy.DEFAULT)
        expect:
        allFilesToFingerprint.each { file ->
            if (file.isFile() || !file.exists()) {
                assert fingerprints[file] == IGNORED
            } else {
                assert fingerprints[file] == null
            }
        }
    }

    def "sensitivity NAME_ONLY (DirectorySensitivity: #strategy.directorySensitivity)"() {
        def fingerprints = collectFingerprints(strategy)
        expect:
        (getAllFilesToFingerprint(strategy.directorySensitivity) - emptyRootDir - resources).each { file ->
            assert fingerprints[file] == file.name
        }
        fingerprints[emptyRootDir] == rootDirectoryFingerprintFor(strategy.directorySensitivity)
        fingerprints[resources] == rootDirectoryFingerprintFor(strategy.directorySensitivity)

        where:
        strategy << [
            NameOnlyFingerprintingStrategy.DEFAULT,
            NameOnlyFingerprintingStrategy.IGNORE_DIRECTORIES
        ]
    }

    def "sensitivity RELATIVE (DirectorySensitivity: #strategy.directorySensitivity)"() {
        def fingerprints = collectFingerprints(strategy)
        expect:
        fingerprints[jarFile1]                      == jarFile1.name
        fingerprints[jarFile2]                      == jarFile2.name
        fingerprints[resources]                     == null
        fingerprints[resources.file(fileInRoot)]    == fileInRoot
        fingerprints[resources.file(subDirA)]       == directoryFingerprintFor(subDirA, strategy.directorySensitivity)
        fingerprints[resources.file(fileInSubdirA)] == fileInSubdirA
        fingerprints[resources.file(subDirB)]       == directoryFingerprintFor(subDirB, strategy.directorySensitivity)
        fingerprints[resources.file(fileInSubdirB)] == fileInSubdirB
        fingerprints[emptyRootDir]                  == null
        fingerprints[missingFile]                   == null

        where:
        strategy << [
            new RelativePathFingerprintingStrategy(STRING_INTERNER, DirectorySensitivity.DEFAULT),
            new RelativePathFingerprintingStrategy(STRING_INTERNER, DirectorySensitivity.IGNORE_DIRECTORIES)
        ]
    }

    def "sensitivity ABSOLUTE (DirectorySensitivity: #strategy.directorySensitivity)"() {
        def fingerprints = collectFingerprints(strategy)
        expect:
        def allFilesToFingerprint = getAllFilesToFingerprint(strategy.directorySensitivity)
        allFilesToFingerprint.each { file ->
            assert fingerprints[file] == file.absolutePath
        }
        fingerprints.size() == allFilesToFingerprint.size()

        where:
        strategy << [
            AbsolutePathFingerprintingStrategy.DEFAULT,
            AbsolutePathFingerprintingStrategy.IGNORE_DIRECTORIES
        ]
    }

    String rootDirectoryFingerprintFor(DirectorySensitivity directorySensitivity) {
        return directorySensitivity == DirectorySensitivity.DEFAULT ? IGNORED : null
    }

    String directoryFingerprintFor(String value, DirectorySensitivity directorySensitivity) {
        return directorySensitivity == DirectorySensitivity.DEFAULT ? value : null
    }

    List<File> getAllFilesToFingerprint() {
        return getAllFilesToFingerprint(DirectorySensitivity.DEFAULT)
    }

    List<File> getAllFilesToFingerprint(DirectorySensitivity directorySensitivity) {
        def dirs = [emptyRootDir, resources.file(subDirA), resources.file(subDirB), resources]
        def files = [jarFile1, jarFile2, resources.file(fileInRoot), resources.file(fileInSubdirA), resources.file(fileInSubdirB)]

        return directorySensitivity == DirectorySensitivity.DEFAULT ? (dirs + files) : files
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
