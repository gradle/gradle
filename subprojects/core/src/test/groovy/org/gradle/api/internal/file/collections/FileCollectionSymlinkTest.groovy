package org.gradle.api.internal.file.collections

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project

import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll

// cannot use org.gradle.util.Resources here because the files it returns have already been canonicalized
class FileCollectionSymlinkTest extends Specification {
    @Shared Project project = new ProjectBuilder().build()

    @Shared File file = getTestFile("file")
    @Shared File symlink = getTestFile("symlink")
    @Shared File symlinked = getTestFile("symlinked")
    @Shared File baseDir = file.parentFile

    @Unroll("#desc can handle symlinks")
    def "file collection can handle symlinks"() {
        expect:
        fileCollection.contains(file)
        fileCollection.contains(symlink)
        fileCollection.contains(symlinked)
        fileCollection.files == [file, symlink, symlinked] as Set

        (fileCollection - project.files(symlink)).files == [file, symlinked] as Set

        where:
        desc                 | fileCollection
        "project.files()"    | project.files(file, symlink, symlinked)
        "project.fileTree()" | project.fileTree(baseDir)
    }

    private static File getTestFile(String name) {
        new File(FileCollectionSymlinkTest.getResource(name).toURI().path)
    }
}
