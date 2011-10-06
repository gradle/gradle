package org.gradle.api.file

import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec

import spock.lang.Unroll

class FileCollectionSymlinkIntegrationTest extends AbstractIntegrationSpec {
    @Unroll("#desc can handle symlinks")
    def "file collection can handle symlinks"() {
        def buildScript = file("build.gradle")
        def baseDir = getTestFile("file").getParentFile()

        buildScript << """
def baseDir = new File("$baseDir")
def file = new File(baseDir, "file")
def symlink = new File(baseDir, "symlink")
def symlinked = new File(baseDir, "symlinked")
def fileCollection = $code

assert fileCollection.contains(file)
assert fileCollection.contains(symlink)
assert fileCollection.contains(symlinked)
assert fileCollection.files == [file, symlink, symlinked] as Set
assert (fileCollection - project.files(symlink)).files == [file, symlinked] as Set
        """

        when:
        executer.usingBuildScript(buildScript).run()

        then:
        noExceptionThrown()

        where:
        desc                 | code
        "project.files()"    | "project.files(file, symlink, symlinked)"
        "project.fileTree()" | "project.fileTree(baseDir)"
    }

    private File getTestFile(String name) {
        new File(getClass().getResource(name).toURI().path)
    }
}
