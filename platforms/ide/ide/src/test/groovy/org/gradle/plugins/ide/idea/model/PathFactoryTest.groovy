/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class PathFactoryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final PathFactory factory = new PathFactory()

    def createsPathForAFileUnderARootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.path(tmpDir.file('a', 'b'))
        path.url == 'file://$ROOT_DIR$/a/b'
        path.relPath == '$ROOT_DIR$/a/b'
    }

    def createsPathForAFileNotUnderARootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)
        def file = tmpDir.testDirectory.parentFile.file('a')
        def relpath = relpath(file)

        expect:
        def path = factory.path(file)
        path.url == "file://$relpath"
        path.relPath == relpath
    }

    def usesTheClosestAncestorRootDirForAFileUnderMultipleRootDirs() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)
        factory.addPathVariable('SUB_DIR', tmpDir.file('sub'))

        expect:
        def path = factory.path(tmpDir.file('sub', 'a'))
        path.url == 'file://$SUB_DIR$/a'
        path.relPath == '$SUB_DIR$/a'
    }

    def createsPathForARootDir() {
        factory.addPathVariable('SUB_DIR', tmpDir.file('sub'))
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def rootDir = factory.path(tmpDir.testDirectory)
        rootDir.url == 'file://$ROOT_DIR$/'
        rootDir.relPath == '$ROOT_DIR$/'

        def subDir = factory.path(tmpDir.file('sub'))
        subDir.url == 'file://$SUB_DIR$/'
        subDir.relPath == '$SUB_DIR$/'
    }

    def createsPathForAJarFile() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.path(tmpDir.file('a.jar'))
        path.url == 'jar://$ROOT_DIR$/a.jar!/'
        path.relPath == '$ROOT_DIR$/a.jar'
    }

    def createsRelativePathForADescendantOfRootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.relativePath('ROOT_DIR', tmpDir.file('a/b'))
        path.url == 'file://$ROOT_DIR$/a/b'
        path.relPath == '$ROOT_DIR$/a/b'
    }

    def createsRelativePathForAnAncestorOfRootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.relativePath('ROOT_DIR', tmpDir.testDirectory.parentFile.parentFile.file('a/b'))
        path.url == 'file://$ROOT_DIR$/../../a/b'
        path.relPath == '$ROOT_DIR$/../../a/b'
    }

    def createsRelativePathForASiblingOfRootDir() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.relativePath('ROOT_DIR', tmpDir.testDirectory.parentFile.file('a'))
        path.url == 'file://$ROOT_DIR$/../a'
        path.relPath == '$ROOT_DIR$/../a'
    }

    def createsRelativePathForAFileOnAnotherFileSystem() {
        def fileSystemRoots = findFileSystemRoots()
        if (fileSystemRoots.size() == 1) {
            return
        }
        def rootDir = new File(fileSystemRoots[0], 'root')
        def file = new File(fileSystemRoots[1], 'file')
        def relpath = relpath(file)
        factory.addPathVariable('ROOT_DIR', rootDir)

        expect:
        def path = factory.relativePath('ROOT_DIR', file)
        path.url == "file://${relpath}"
        path.relPath == relpath
    }

    def createsPathForAFileUrl() {
        expect:
        def path = factory.path('file://a/b/c')
        path.url == 'file://a/b/c'
        path.relPath == null
    }

    def createsPathForAJarUrl() {
        expect:
        def path = factory.path('jar://a/b/c.jar!/some/entry')
        path.url == 'jar://a/b/c.jar!/some/entry'
        path.relPath == null
    }

    def createsPathForAUrlWithUnknownScheme() {
        expect:
        def path = factory.path('other:abc')
        path.url == 'other:abc'
        path.relPath == null
    }

    def createsPathForAUrlAndRelPath() {
        expect:
        def path = factory.path('file://a/b/c', '/path')
        path.url == 'file://a/b/c'
        path.relPath == '/path'
    }

    def createsPathForAUrlWithPathVariables() {
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:
        def path = factory.path('file://$ROOT_DIR$/c')
        path.url == 'file://$ROOT_DIR$/c'
        path.relPath == null
    }

    def filePathsAreEqualWhenTheyPointToTheSameFile() {
        TestFile subDir = tmpDir.file('sub')
        TestFile childFile = tmpDir.file('sub/a/b')

        factory.addPathVariable('SUB_DIR', subDir)
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:

        // Using files
        factory.path(subDir) == factory.path(subDir)
        factory.path(childFile) == factory.path(childFile)
        factory.path(childFile) != factory.path(subDir)

        // Using normalised absolute urls
        factory.path(subDir) == factory.path("file://${relpath(subDir)}")
        factory.path(subDir) == factory.path("file://${relpath(childFile)}/../..")
        factory.path("file://${relpath(subDir)}") != factory.path("file://${relpath(childFile)}")

        // Using absolute paths
        factory.path(subDir) == factory.path("file://${subDir.absolutePath}")

        // Using replacement variables
        factory.path(childFile) == factory.path('file://$SUB_DIR$/a/b')
        factory.path(childFile) == factory.path('file://$SUB_DIR$/c/../a/b')
        factory.path('file://$ROOT_DIR$/sub') == factory.path('file://$SUB_DIR$')
        factory.path('file://$ROOT_DIR$') != factory.path('file://$SUB_DIR$')
    }

    def filePathsAreEqualWhenTheyPointToTheSameEntryInTheSameFile() {
        TestFile subDir = tmpDir.file('sub')
        TestFile childFile = tmpDir.file('sub/a/b.jar')

        factory.addPathVariable('SUB_DIR', subDir)
        factory.addPathVariable('ROOT_DIR', tmpDir.testDirectory)

        expect:

        // Using files
        factory.path(childFile) == factory.path(childFile)
        factory.path(childFile) != factory.path(subDir)

        // Using normalised absolute urls
        factory.path(childFile) == factory.path("jar://${relpath(childFile)}!/")
        factory.path("jar://${relpath(childFile)}!/entry") == factory.path("jar://${relpath(childFile)}!/entry")
        factory.path(childFile) != factory.path("jar://${relpath(childFile)}!/entry")

        // Using replacement variables
        factory.path(childFile) == factory.path('jar://$SUB_DIR$/a/b.jar!/')
        factory.path(childFile) == factory.path('jar://$SUB_DIR$/c/../a/b.jar!/')

        factory.path(childFile) != factory.path('jar://$SUB_DIR$/a/b.jar')
        factory.path(childFile) != factory.path("file://${relpath(childFile)}")
    }

    private String relpath(File file) {
        return file.absolutePath.replace(File.separator, '/')
    }

    def findFileSystemRoots() {
        File.listRoots().inject([]) {List result, File root ->
            try {
                new File(root, 'file').canonicalFile
                result << root
            } catch (IOException e) {
                // skip
            }
            return result
        }
    }

}
