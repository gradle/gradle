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
package org.gradle.plugins.idea.model

import org.gradle.util.Matchers
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class PathTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()

    def generatesUrlAndPathForFileInRootDir() {
        expect:
        def path = new Path(tmpDir.dir, '$ROOT_DIR$', tmpDir.file('a', 'b'))
        path.url == 'file://$ROOT_DIR$/a/b'
        path.relPath == '$ROOT_DIR$/a/b'
    }

    def generatesUrlAndPathForRootDir() {
        expect:
        def path = new Path(tmpDir.dir, '$ROOT_DIR$', tmpDir.dir)
        path.url == 'file://$ROOT_DIR$/'
        path.relPath == '$ROOT_DIR$/'
    }

    def generatesUrlAndPathForAncestorOfRootDir() {
        expect:
        def path = new Path(tmpDir.dir, '$ROOT_DIR$', tmpDir.dir.parentFile.parentFile)
        path.url == 'file://$ROOT_DIR$/../../'
        path.relPath == '$ROOT_DIR$/../../'
    }

    def generatesUrlAndPathForSiblingOfRootDir() {
        expect:
        def path = new Path(tmpDir.dir, '$ROOT_DIR$', tmpDir.dir.parentFile.file('a'))
        path.url == 'file://$ROOT_DIR$/../a'
        path.relPath == '$ROOT_DIR$/../a'
    }

    def generatesUrlAndPathForJarFileInRootDir() {
        expect:
        def path = new Path(tmpDir.dir, '$ROOT_DIR$', tmpDir.file('a', 'b.jar'))
        path.url == 'jar://$ROOT_DIR$/a/b.jar!/'
        path.relPath == '$ROOT_DIR$/a/b.jar'
    }

    def generatesUrlAndPathForFileWithNoRootDir() {
        def file = tmpDir.file('a')
        def relpath = file.absolutePath.replace(File.separator, '/')

        expect:
        def path = new Path(file)
        path.url == "file://${relpath}"
        path.relPath == relpath
    }

    def generatesUrlAndPathForFileOnDifferentFilesystemToRootDir() {
        def fileSystemRoots = findFileSystemRoots()
        if (fileSystemRoots.size() == 1) {
            return
        }
        def rootDir = new File(fileSystemRoots[0], 'root')
        def file = new File(fileSystemRoots[1], 'file')
        def relpath = file.absolutePath.replace(File.separator, '/')

        expect:
        def path = new Path(rootDir, '$ROOT_DIR$', file)
        path.url == "file://${relpath}"
        path.relPath == relpath
    }

    def generatesUrlAndPathForJarFileWithNoRootDir() {
        def file = tmpDir.file('a.jar')
        def relpath = file.absolutePath.replace(File.separator, '/')

        expect:
        def path = new Path(file)
        path.url == "jar://${relpath}!/"
        path.relPath == relpath
    }

    def pathsAreEqualWhenTheyHaveTheSameCanonicalUrl() {
        expect:
        Matchers.strictlyEquals(new Path('file://$ROOT_DIR$/file'), new Path('file://$ROOT_DIR$/file'))
        new Path('file://$ROOT_DIR$/file') != new Path('file://$ROOT_DIR$/other')
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
