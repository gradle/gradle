/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file


import org.gradle.api.provider.Provider
import org.gradle.internal.typeconversion.UnsupportedNotationException
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.UsesNativeServices
import org.junit.Assume
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.Callable

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

@UsesNativeServices
class BaseDirFileResolverSpec extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    @Requires(UnitTestPreconditions.Symlinks)
    def "normalizes absolute path which points to an absolute link"() {
        def target = createFile(new File(tmpDir.testDirectory, 'target.txt'))
        def file = new File(tmpDir.testDirectory, 'a/other.txt')
        createLink(file, target)
        assert file.exists() && file.file

        expect:
        normalize(file) == file
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "normalizes absolute path which points to a relative link"() {
        def target = createFile(new File(tmpDir.testDirectory, 'target.txt'))
        def file = new File(tmpDir.testDirectory, 'a/other.txt')
        createLink(file, '../target.txt')
        assert file.exists() && file.file

        expect:
        normalize(file) == file
    }

    @Requires(UnitTestPreconditions.CaseInsensitiveFs)
    def "does not normalize case"() {
        def file = createFile(new File(tmpDir.testDirectory, 'dir/file.txt'))
        def path = new File(tmpDir.testDirectory, 'dir/FILE.txt')
        assert path.exists() && path.file

        expect:
        normalize(path) == path
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "normalizes path which points to a link to something that does not exist"() {
        def file = new File(tmpDir.testDirectory, 'a/other.txt')
        createLink(file, 'unknown.txt')
        assert !file.exists() && !file.file

        expect:
        normalize(file) == file
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "normalizes path when ancestor is an absolute link"() {
        def target = createFile(new File(tmpDir.testDirectory, 'target/file.txt'))
        def file = new File(tmpDir.testDirectory, 'a/b/file.txt')
        createLink(file.parentFile, target.parentFile)
        assert file.exists() && file.file

        expect:
        normalize(file) == file
    }

    def "normalizes relative path"() {
        def ancestor = new File(tmpDir.testDirectory, "test")
        def baseDir = new File(ancestor, "base")
        def sibling = new File(ancestor, "sub")
        def child = createFile(new File(baseDir, "a/b/file.txt"))

        expect:
        normalize("a/b/file.txt", baseDir) == child
        normalize("./a/b/file.txt", baseDir) == child
        normalize(".//a/b//file.txt", baseDir) == child
        normalize("sub/../a/b/file.txt", baseDir) == child
        normalize("../sub", baseDir) == sibling
        normalize("..", baseDir) == ancestor
        normalize(".", baseDir) == baseDir
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "normalizes relative path when base dir is a link"() {
        createFile(new File(tmpDir.testDirectory, 'target/file.txt'))
        def baseDir = new File(tmpDir.testDirectory, 'base')
        createLink(baseDir, "target")
        def file = new File(baseDir, 'file.txt')
        assert file.exists() && file.file

        expect:
        normalize('file.txt', baseDir) == file
    }

    @Requires(UnitTestPreconditions.Windows)
    def "does not normalize windows 8.3 names"() {
        createFile(new File(tmpDir.testDirectory, 'dir/file-with-long-name.txt'))
        def path = new File(tmpDir.testDirectory, 'dir/FILE-W~1.TXT')
        Assume.assumeTrue(path.exists() && path.file)

        expect:
        normalize(path) == path
    }

    def "normalizes file system roots"() {
        expect:
        normalize(root) == new File(root)

        where:
        root << getFsRoots().collect { it.absolutePath }
    }

    @Requires(UnitTestPreconditions.Windows)
    def "normalizes non-existent file system root"() {
        def file = nonexistentFsRoot()
        assert !file.exists()
        assert file.absolute

        expect:
        normalize(file) == file
    }

    def "normalizes relative path that refers to ancestor of file system root"() {
        File root = getFsRoots()[0]

        expect:
        normalize("../../..", root) == root
    }

    def "cannot resolve file using unsupported notation"() {
        when:
        resolver().resolve(12)

        then:
        UnsupportedNotationException e = thrown()
        e.message == toPlatformLineSeparators("""Cannot convert the provided notation to a File or URI: 12.
The following types/formats are supported:
  - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
  - A String or CharSequence URI, for example 'file:/usr/include'.
  - A File instance.
  - A Path instance.
  - A Directory instance.
  - A RegularFile instance.
  - A URI or URL instance.
  - A TextResource instance.""")
    }

    def "normalizes null-returning closure to null"() {
        def ancestor = new File(tmpDir.testDirectory, "test")
        def baseDir = new File(ancestor, "base")

        when:
        normalize(new Callable() {
            @Override
            Object call() throws Exception {
                return null
            }

            @Override
            String toString() {
                return "null returning Callable"
            }
        }, baseDir)
        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot convert path to File. path='null returning Callable'"
    }

    def "normalizes Provider value"() {
        def baseDir = tmpDir.testDirectory.file("base")
        def file = tmpDir.testDirectory.file("test")
        def provider1 = Stub(Provider)
        provider1.get() >> file
        def provider2 = Stub(Provider)
        provider2.get() >> "value"

        expect:
        normalize(provider1, baseDir) == file
        normalize(provider2, baseDir) == baseDir.file("value")
    }

    def "does not allow resolving null URI"() {
        when:
        resolver(tmpDir.testDirectory).resolveUri(null)
        then:
        def ex = thrown UnsupportedNotationException
        ex.message.contains "Cannot convert a null value to a File or URI."
    }

    def createLink(File link, File target) {
        createLink(link, target.absolutePath)
    }

    def createLink(File link, String target) {
        new TestFile(link).createLink(target)
    }

    def createFile(File file) {
        file.parentFile.mkdirs()
        file.text = 'content'
        file
    }

    def normalize(Object path, File baseDir = tmpDir.testDirectory) {
        resolver(baseDir).resolve(path)
    }

    private BaseDirFileResolver resolver(File baseDir = tmpDir.testDirectory) {
        new BaseDirFileResolver(baseDir)
    }

    private File[] getFsRoots() {
        File.listRoots().findAll { !it.absolutePath.startsWith("A:") }
    }

    private File nonexistentFsRoot() {
        ('Z'..'A').collect {
            "$it:\\"
        }.findResult {
            new File(it).exists() ? null : new File(it)
        }
    }
}
