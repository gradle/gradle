/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.file.FileType
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

@Requires(UnitTestPreconditions.Symlinks)
@CleanupTestDirectory
class BrokenSymlinkNormalizationStrategyTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    private static final StringInterner STRING_INTERNER = new StringInterner()

    def fileSystemAccess = TestFiles.fileSystemAccess()

    def "root broken symlink is ignored for #strategyName"() {
        given:
        def root = file('root')
        root.createLink(file('non-existing'))

        when:
        def fingerprints = strategy.collectFingerprints(snapshot(root))

        then:
        fingerprints.isEmpty()

        where:
        strategy << allFingerprintingStrategies
        strategyName = getStrategyName(strategy)
    }

    def "non-root broken symlink is fingerprinted as missing for #strategyName"() {
        given:
        def root = file('root')
        root.mkdirs()
        def brokenSymlink = root.file('broken-symlink')
        brokenSymlink.createLink(file('non-existing'))

        when:
        def fingerprints = strategy.collectFingerprints(snapshot(root))

        then:
        fingerprints[brokenSymlink.absolutePath].type == FileType.Missing

        where:
        strategy << allFingerprintingStrategies
        strategyName = getStrategyName(strategy)
    }

    private static List<FingerprintingStrategy> getAllFingerprintingStrategies() {
        return [
            IgnoredPathFingerprintingStrategy.DEFAULT,
            AbsolutePathFingerprintingStrategy.DEFAULT,
            AbsolutePathFingerprintingStrategy.IGNORE_DIRECTORIES,
            NameOnlyFingerprintingStrategy.DEFAULT,
            NameOnlyFingerprintingStrategy.IGNORE_DIRECTORIES,
        ] + DirectorySensitivity.values().collect { directorySensitivity -> new RelativePathFingerprintingStrategy(STRING_INTERNER, directorySensitivity) }
    }

    private static String getStrategyName(FingerprintingStrategy strategy) {
        "${strategy.identifier}${strategy instanceof AbstractDirectorySensitiveFingerprintingStrategy ? " (${strategy.directorySensitivity})" : ""}"
    }

    private FileSystemLocationSnapshot snapshot(File file) {
        return fileSystemAccess.read(file.absolutePath)
    }

    protected TestFile file(String... path) {
        temporaryFolder.file(path)
    }
}
