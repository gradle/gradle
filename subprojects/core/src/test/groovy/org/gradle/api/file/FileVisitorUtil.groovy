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

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class FileVisitorUtil {
    static def assertCanStopVisiting(FileTree tree) {
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
    
    static def assertVisits(FileTree tree, Iterable<String> expectedFiles, Iterable<String> expectedDirs) {
        Set files = [] as Set
        Set dirs = [] as Set
        FileVisitor visitor = [
                visitFile: {FileVisitDetails details ->
                    if (details.relativePath.parent.parent) {
                        assertThat(dirs, hasItem(details.relativePath.parent.pathString))
                    }
                    assertTrue(files.add(details.relativePath.pathString))
                    assertTrue(details.relativePath.isFile())
                    assertEquals(details.file.lastModified(), details.lastModified)
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
                    assertEquals(details.file.lastModified(), details.lastModified)
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

}
