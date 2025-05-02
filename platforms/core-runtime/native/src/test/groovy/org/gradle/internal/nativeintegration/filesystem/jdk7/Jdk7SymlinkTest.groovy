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

package org.gradle.internal.nativeintegration.filesystem.jdk7

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.WindowsSymbolicLinkUtil.createWindowsHardLinks
import static org.gradle.util.WindowsSymbolicLinkUtil.createWindowsJunction
import static org.gradle.util.WindowsSymbolicLinkUtil.createWindowsSymbolicLink

class Jdk7SymlinkTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Requires(UnitTestPreconditions.Symlinks)
    def 'on symlink supporting system, it will return true for supported symlink'() {
        expect:
        new Jdk7Symlink(TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.createDir("tmp"))).isSymlinkCreationSupported()
    }

    @Requires(UnitTestPreconditions.NoSymlinks)
    def 'on non symlink supporting system, it will return false for supported symlink'() {
        expect:
        !new WindowsJdk7Symlink().isSymlinkCreationSupported()
    }

    def 'deletes test files after symlink support test with #create'() {
        expect:
        listSymlinkTestFiles().findAll { !it.delete() }.empty
        create(temporaryFolder.createDir("tmp"))
        listSymlinkTestFiles().empty

        where:
        create << [{ it -> new Jdk7Symlink(TestFiles.tmpDirTemporaryFileProvider(it)) }, { new WindowsJdk7Symlink() }]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def 'can create and detect symlinks'() {
        def symlink = new Jdk7Symlink(TestFiles.tmpDirTemporaryFileProvider(temporaryFolder.createDir("tmp")))
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        symlink.symlink(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testFile'))

        when:
        symlink.symlink(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'can detect Windows symbolic links as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsSymbolicLink(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testFile'))

        when:
        createWindowsSymbolicLink(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'does not detect Windows hard links as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsHardLinks(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        !symlink.isSymlink(new File(testDirectory, 'testFile'))
    }

    @Requires(UnitTestPreconditions.Windows)
    def 'can detect Windows junction point as symbolic links'() {
        def symlink = new WindowsJdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        createWindowsJunction(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))

        cleanup:
        // Need to delete the junction point manually because it's not supported by JDK
        // See: https://bugs.openjdk.java.net/browse/JDK-8069345
        new File(testDirectory, 'testDir').delete()
    }

    private static List<File> listSymlinkTestFiles() {
        def tempDir = new File(SystemProperties.getInstance().getJavaIoTmpDir())
        return tempDir.listFiles(new FileFilter() {
            @Override
            boolean accept(File pathname) {
                return pathname.name.startsWith("symlink") && (pathname.name.endsWith("test") || pathname.name.endsWith("test_link")) && pathname.canWrite()
            }
        })
    }
}
