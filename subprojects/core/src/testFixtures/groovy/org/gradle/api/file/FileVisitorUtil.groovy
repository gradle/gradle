/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.file

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.FileTreeAdapter
import org.gradle.api.internal.file.collections.MinimalFileTree

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.hasItem
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class FileVisitorUtil {
    static void assertCanStopVisiting(MinimalFileTree tree) {
        assertCanStopVisiting(new FileTreeAdapter(tree, TestFiles.patternSetFactory))
    }

    static void assertCanStopVisiting(FileTree tree) {
        boolean found = false
        FileVisitor visitor = [
                visitFile: {FileVisitDetails details ->
                    assertFalse(found)
                    found = true
                    details.stopVisiting()
                },
                visitDir: {FileVisitDetails details ->
                    assertFalse(found)
                }
        ] as FileVisitor

        tree.visit(visitor)
        assertTrue(found)
    }

    static void assertVisits(MinimalFileTree tree, Iterable<String> expectedFiles, Iterable<String> expectedDirs) {
        assertVisits(new FileTreeAdapter(tree, TestFiles.patternSetFactory), expectedFiles, expectedDirs)
    }

    static void assertVisits(FileTree tree, Iterable<String> expectedFiles, Iterable<String> expectedDirs) {
        Set files = [] as Set
        Set dirs = [] as Set
        FileVisitor visitor = [
                visitFile: {FileVisitDetails details ->
                    if (details.relativePath.parent.parent) {
                        assertThat(dirs, hasItem(details.relativePath.parent.pathString))
                    }
                    assertTrue(files.add(details.relativePath.pathString))
                    assertTrue(details.relativePath.isFile())
                    assertTrue(details.file.file)
                    ByteArrayOutputStream outstr = new ByteArrayOutputStream()
                    details.copyTo(outstr)
                    assertEquals(details.file.text, outstr.toString())
                },
                visitDir: {FileVisitDetails details ->
                    if (details.relativePath.parent.parent) {
                        assertThat(dirs, hasItem(details.relativePath.parent.pathString))
                    }
                    assertTrue(dirs.add(details.relativePath.pathString))
                    assertFalse(details.relativePath.isFile())
                    assertTrue(details.file.directory)
                }
        ] as FileVisitor

        tree.visit(visitor)

        assertThat(files, equalTo(expectedFiles as Set))
        assertThat(dirs, equalTo(expectedDirs as Set))

        files = [] as Set
        tree.visit {FileVisitDetails details ->
            assertTrue(files.add(details.relativePath.pathString))
        }

        assertThat(files, equalTo(expectedFiles + expectedDirs as Set))
    }

    static void assertVisits(FileTree tree, Map<String, File> files) {
        Map<String, File> visited = [:]
        FileVisitor visitor = [
                visitFile: {FileVisitDetails details ->
                    visited.put(details.path, details.file)
                },
                visitDir: {FileVisitDetails details ->
                }
        ] as FileVisitor

        tree.visit(visitor)

        assertThat(visited, equalTo(files))
    }

    static void assertVisitsPermissions(MinimalFileTree tree, Map<String, Integer> filesWithPermissions) {
        assertVisitsPermissions(new FileTreeAdapter(tree, TestFiles.patternSetFactory), filesWithPermissions)
    }

    static void assertVisitsPermissions(FileTree tree, Map<String, Integer> filesWithPermissions) {
        def visited = [:]
        tree.visit {
            visited[it.name] = it.mode
        }
        assertThat(visited, equalTo(filesWithPermissions))
    }
}
