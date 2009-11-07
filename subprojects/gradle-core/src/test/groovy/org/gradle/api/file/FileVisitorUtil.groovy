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
