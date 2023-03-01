/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.tasks.bundling

import org.apache.commons.lang.RandomStringUtils
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.tasks.Copy
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.ArchiveTestFixture
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import spock.lang.Issue

import static org.hamcrest.CoreMatchers.equalTo

@TestReproducibleArchives
class ArchiveIntegrationTest extends AbstractIntegrationSpec {
    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    def canCopyFromAZip() {
        given:
        createZip('test.zip') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.txt'
                file 'file2.xml'
            }
        }

        and:
        buildFile << '''
            task copy(type: Copy) {
                from zipTree('test.zip')
                exclude '**/*.xml'
                into 'dest'
            }
'''
        when:
        run 'copy'

        then:
        result.assertTaskExecuted(":copy")
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')

        when:
        run 'copy'

        then:
        result.assertTasksSkipped(":copy")

        when:
        createZip('test.zip') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.xml'
            }
            file 'file3.txt'
        }

        run 'copy'

        then:
        result.assertTasksExecutedAndNotSkipped(":copy")
        // Copy (intentionally) leaves stuff behind
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt', 'file3.txt')

        when:
        run 'copy'

        then:
        result.assertTasksSkipped(":copy")
    }

    def cannotCreateAnEmptyTar() {
        given:
        buildFile << """
            task tar(type: Tar) {
                from 'test'
                destinationDirectory = buildDir
                archiveFileName = 'test.tar'
            }
            """
        when:
        run "tar"

        then:
        file('build/test.tar').assertDoesNotExist()
    }

    def canCopyFromATar() {
        given:
        createTar('test.tar') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.txt'
                file 'file2.xml'
            }
        }

        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree('test.tar')
                exclude '**/*.xml'
                into 'dest'
            }
'''
        when:
        run 'copy'

        then:
        result.assertTaskExecuted(":copy")
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')

        when:
        run 'copy'

        then:
        result.assertTasksSkipped(":copy")

        when:
        createTar('test.tar') {
            subdir1 {
                file 'file1.txt'
            }
            subdir2 {
                file 'file2.xml'
            }
            file 'file3.txt'
        }

        run 'copy'

        then:
        result.assertTasksExecutedAndNotSkipped(":copy")
        // Copy (intentionally) leaves stuff behind
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt', 'file3.txt')

        when:
        run 'copy'

        then:
        result.assertTasksSkipped(":copy")
    }

    def "handles gzip compressed tars"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
                file '2.txt'
            }
        }
        tar.tgzTo(file('test.tgz'))
        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree('test.tgz')
                exclude '**/2.txt'
                into 'dest'
            }
'''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
    }

    @Issue("GRADLE-3310")
    def "handles gzip compressed tars from resources.gzip"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
                file '2.txt'
            }
        }
        tar.tgzTo(file('test.tgz'))
        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree(resources.gzip('test.tgz'))
                exclude '**/2.txt'
                into 'dest'
            }
'''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
    }

    def "cannot provide custom resource for the tarTree"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
            }
        }
        tar.tarTo(file('test.tar'))
        and:
        buildFile << '''
            def res = new ReadableResource() {
                InputStream read() { new FileInputStream(file('test.tar')) }
                String getBaseName() { "foo" }
                URI getURI() { new java.net.URI("foo") }
                String getDisplayName() { "The foo" }
            }

            task copy(type: Copy) {
                from tarTree(res)
                into 'dest'
            }
'''
        when:
        fails 'copy'
        then:
        failureHasCause("Cannot use tarTree() on a resource without a backing file.")
    }

    def "handles bzip2 compressed tars"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
                file '2.txt'
            }
        }
        tar.tbzTo(file('test.tbz2'))
        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree('test.tbz2')
                exclude '**/2.txt'
                into 'dest'
            }
'''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
    }

    @Issue("GRADLE-3310")
    def "handles bzip2 compressed tars from resources.bzip2"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
                file '2.txt'
            }
        }
        tar.tbzTo(file('test.tbz2'))
        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree(resources.bzip2('test.tbz2'))
                exclude '**/2.txt'
                into 'dest'
            }
'''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
    }

    def "knows compression of the tar"() {
        given:
        buildFile << '''
            task myTar(type: Tar) {
                destinationDirectory = buildDir

                assert compression == Compression.NONE

                compression = Compression.GZIP
                assert compression == Compression.GZIP

                compression = Compression.BZIP2
                assert compression == Compression.BZIP2
            }
'''

        expect:
        run 'myTar'
    }

    def "can choose compression method for tarTree"() {
        given:
        TestFile tar = file('tar-contents')
        tar.create {
            someDir {
                file '1.txt'
                file '2.txt'
            }
        }
        //file extension is non-standard:
        tar.tbzTo(file('test.ext'))

        and:
        buildFile << '''
            task copy(type: Copy) {
                from tarTree(resources.bzip2('test.ext'))
                exclude '**/2.txt'
                into 'dest'
            }
'''
        when:
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
    }

    def "zipTreeFailsGracefully when #scenario"() {
        given:
        content.call(getTestDirectory())
        buildFile << '''
            task copy(type: Copy) {
                from zipTree('compressedTarWithWrongExtension.zip')
                into 'dest'
            }
        '''.stripIndent()

        when:
        def failure = runAndFail('copy')

        then:
        failure.assertHasDescription("Execution failed for task ':copy'.")
        failure.assertThatCause(CoreMatchers.startsWith("Cannot expand ZIP"))

        where:
        scenario | content
        "archive of other format"   | { td -> td.file('content/some-file.txt').text = "Content"; td.file('content').tarTo(td.file('compressedTarWithWrongExtension.zip')) }
        "random file"               | { td -> td.file('compressedZipWithWrongExtension.tar').text = "MamboJumbo" }
    }

    def "tarTreeFailsGracefully when #scenario"() {
        given:
        content.call(getTestDirectory())
        buildFile << '''
            task copy(type: Copy) {
                from tarTree('compressedZipWithWrongExtension.tar')
                into 'dest'
            }
        '''.stripIndent()

        when:
        def failure = runAndFail('copy')

        then:
        failure.assertHasDescription("Execution failed for task ':copy'.")
        failure.assertThatCause(CoreMatchers.startsWith("Unable to expand TAR"))

        where:
        scenario | content
        "archive of other format"   | { td -> td.file('content/some-file.txt').text = "Content"; td.file('content').zipTo(td.file('compressedZipWithWrongExtension.tar')) }
        "random file"               | { td -> td.file('compressedZipWithWrongExtension.tar').text = "MamboJumbo" }
    }

    def cannotCreateAnEmptyZip() {
        given:
        buildFile << '''
            task zip(type: Zip) {
                from 'test'
                destinationDirectory = buildDir
                archiveFileName = 'test.zip'
            }
        '''
        when:
        run 'zip'
        then:
        file('build/test.zip').assertDoesNotExist()
    }

    def canCreateAZipArchive() {
        given:
        createDir('test') {
            dir1 {
                file('file1.txt').write("abc")
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'script.sh'
                file 'config.properties'
            }
        }
        and:
        buildFile << '''
            task zip(type: Zip) {
                into('prefix') {
                    from 'test'
                    include '**/*.txt'
                    rename { "renamed_$it" }
                    filter { "[$it]" }
                }
                into('scripts') {
                    from 'test'
                    include '**/*.sh'
                }
                into('conf') {
                    from 'test'
                    include '**/*.properties'
                    rename { null }
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.zip'
            }
        '''
        when:
        run 'zip'
        then:
        def expandDir = file('expanded')
        file('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
            'prefix/dir1/renamed_file1.txt',
            'prefix/renamed_file1.txt',
            'prefix/dir2/renamed_file2.txt',
            'scripts/dir1',
            'scripts/dir2/script.sh',
            'conf/dir1',
            'conf/dir2/config.properties')

        expandDir.file('prefix/dir1/renamed_file1.txt').assertContents(equalTo('[abc]'))
    }

    def canCreateAZipArchiveWithContentsUncompressed() {
        def randomAscii = RandomStringUtils.randomAscii(300)
        given:
        createDir('test') {
            dir1 {
                file('file1.txt').write(randomAscii)
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'script.sh'
            }
        }
        and:
        buildFile << '''
            task uncompressedZip(type: Zip) {
                into('prefix') {
                    from 'test'
                    include '**/*.txt'
                }
                into('scripts') {
                    from 'test'
                    include '**/*.sh'
                }
                destinationDirectory = buildDir
                archiveFileName = 'uncompressedTest.zip'
                entryCompression = ZipEntryCompression.STORED
            }

            task compressedZip(type: Zip) {
                into('prefix') {
                    from 'test'
                    include '**/*.txt'
                }
                into('scripts') {
                    from 'test'
                    include '**/*.sh'
                }
                destinationDirectory = buildDir
                archiveFileName = 'compressedTest.zip'
            }
        '''
        when:
        run 'uncompressedZip'
        run 'compressedZip'
        then:
        def uncompressedSize = file('build/uncompressedTest.zip').length()
        def compressedSize = file('build/compressedTest.zip').length()
        println "uncompressed" + uncompressedSize
        println "compressed" + compressedSize
        assert compressedSize < uncompressedSize

        def expandDir = file('expandedUncompressed')
        file('build/uncompressedTest.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
            'prefix/dir1/file1.txt',
            'prefix/file1.txt',
            'prefix/dir2/file2.txt',
            'scripts/dir1',
            'scripts/dir2/script.sh')

        expandDir.file('prefix/dir1/file1.txt').assertContents(equalTo(randomAscii))

        def expandCompressedDir = file('expandedCompressed')
        file('build/compressedTest.zip').unzipTo(expandCompressedDir)
        expandCompressedDir.assertHasDescendants(
            'prefix/dir1/file1.txt',
            'prefix/file1.txt',
            'prefix/dir2/file2.txt',
            'scripts/dir1',
            'scripts/dir2/script.sh')

        expandCompressedDir.file('prefix/dir1/file1.txt').assertContents(equalTo(randomAscii))
    }

    def canCreateATarArchive() {
        given:
        createDir('test') {
            dir1 {
                file('file1.txt').write 'abc'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'script.sh'
            }
        }
        and:
        buildFile << '''
            task tar(type: Tar) {
                from('test') {
                    include '**/*.txt'
                    filter { "[$it]" }
                }
                from('test') {
                    include '**/*.sh'
                    into 'scripts'
                }
                destinationDirectory = buildDir
                archiveFileName = 'test.tar'
            }
'''
        when:
        run 'tar'
        then:
        def expandDir = file('expanded')
        file('build/test.tar').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt', 'scripts/dir1', 'scripts/dir2/script.sh')

        expandDir.file('dir1/file1.txt').assertContents(equalTo('[abc]'))
    }

    def canCreateATgzArchive() {
        given:
        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'ignored.xml'
            }
        }
        and:
        buildFile << '''
            task tar(type: Tar) {
                compression = Compression.GZIP
                from 'test'
                include '**/*.txt'
                destinationDirectory = buildDir
                archiveFileName = 'test.tgz'
            }
'''
        when:
        run 'tar'
        then:
        def expandDir = file('expanded')
        file('build/test.tgz').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt')
    }

    def canCreateATbzArchive() {
        given:
        createDir('test') {
            dir1 {
                file 'file1.txt'
            }
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
                file 'ignored.xml'
            }
        }
        and:
        buildFile << '''
            task tar(type: Tar) {
                compression = Compression.BZIP2
                from 'test'
                include '**/*.txt'
                destinationDirectory = buildDir
                archiveFileName = 'test.tbz2'
            }
'''
        when:
        run 'tar'
        then:
        def expandDir = file('expanded')
        file('build/test.tbz2').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt')
    }

    def canCreateArchivesAndExplodedImageFromSameSpec() {
        given:
        createDir('test') {
            dir1 {
                file 'file1.txt'
                file 'ignored.xml'
            }
            dir2 {
                dir3 { file 'file2.txt' }
                file 'ignored.xml'
            }
        }
        and:
        buildFile << '''
            def distImage = copySpec {
                include '**/*.txt'
                from('test/dir1') {
                    into 'lib'
                }
                from('test/dir2') {
                    into 'src'
                }
            }
            task copy(type: Copy) {
                into 'build/exploded'
                with distImage
            }
            task zip(type: Zip) {
                destinationDirectory = file('build')
                archiveFileName = 'test.zip'
                into 'prefix'
                with distImage
            }
'''
        when:
        run 'copy', 'zip'
        then:
        file('build/exploded').assertHasDescendants(
            'lib/file1.txt', 'src/dir3/file2.txt'
        )
        def expandDir = file('expanded')
        file('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('prefix/lib/file1.txt', 'prefix/src/dir3/file2.txt')
    }

    def canCreateExplodedImageFromArchiveTask() {
        given:
        createDir('test') {
            dir1 {
                file 'file1.txt'
                file 'ignored.xml'
            }
            dir2 {
                dir3 { file 'file2.txt' }
                file 'ignored.xml'
            }
        }
        and:
        buildFile << '''
            task zip(type: Zip) {
                destinationDirectory = file('build')
                archiveFileName = 'test.zip'
                into 'prefix'
                from 'test'
                include '**/*.txt'
            }
            task explodedZip(type: Copy) {
                into 'build/exploded'
                with zip
            }
            task copyFromRootSpec(type: Copy) {
                into 'build/copy'
                with zip.rootSpec
            }
        '''
        when:
        run 'explodedZip', 'copyFromRootSpec'
        then:
        file('build/exploded').assertHasDescendants(
            'prefix/dir1/file1.txt', 'prefix/dir2/dir3/file2.txt'
        )
        file('build/copy').assertHasDescendants(
            'prefix/dir1/file1.txt', 'prefix/dir2/dir3/file2.txt'
        )
    }

    def canMergeArchivesIntoAnotherZip() {
        given:
        createZip('test.zip') {
            shared {
                file 'zip.txt'
            }
            zipdir1 {
                file 'file1.txt'
            }
        }
        createTar('test.tar') {
            shared {
                file 'tar.txt'
            }
            tardir1 {
                file 'file1.txt'
            }
        }
        createDir('test') {
            shared {
                file 'dir.txt'
            }
            dir1 {
                file 'file1.txt'
            }
        }
        and:
        buildFile << '''
        task zip(type: Zip) {
            from zipTree('test.zip')
            from tarTree('test.tar')
            from fileTree('test')
            destinationDirectory = buildDir
            archiveFileName = 'test.zip'
        }
        '''
        when:
        run 'zip'
        then:
        def expandDir = file('expanded')
        file('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('shared/zip.txt', 'zipdir1/file1.txt', 'shared/tar.txt', 'tardir1/file1.txt', 'shared/dir.txt', 'dir1/file1.txt')
    }

    @Issue("https://github.com/gradle/gradle/issues/9673")
    def "can extract #archiveFile with exclusions"() {
        given:
        "$archive"(archiveFile) {
            lib {
                file("exclude").text = "exclude"
                file("include").text = "include"
            }
        }
        and:
        buildFile << """
        task extract(type: Copy) {
            from $unarchive ("$archiveFile")

            exclude { details ->
                details.isDirectory() ||
                details.file.text.contains('exclude')
            }
            destinationDir = new File(buildDir, "output")
        }
        """
        when:
        succeeds 'extract'
        then:
        file("build/output/lib/exclude").assertDoesNotExist()
        file("build/output/lib/include").assertExists()

        where:
        archiveFile | unarchive | archive
        "test.zip"  | "zipTree" | "createZip"
        "test.tar"  | "tarTree" | "createTar"
    }

    def 'fails when duplicates are included in #archiveType for default duplicates strategy'() {
        given:
        createFilesStructureForDupeTests()
        buildFile << archiveTaskWithDuplicates(archiveType)

        when:
        fails 'archive'

        then:
        failure.assertHasCause "Entry file1.txt is a duplicate but no duplicate handling strategy has been set. Please refer to ${DOCUMENTATION_REGISTRY.getDslRefForProperty(Copy.class, "duplicatesStrategy")} for details."

        where:
        archiveType << ['tar', 'zip']
    }

    def 'ensure duplicates can be included in #archiveType'() {
        given:
        createFilesStructureForDupeTests()
        buildFile << archiveTaskWithDuplicates(archiveType) << """
            archive {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        """
        when:
        run 'archive'

        def archive = archiveFixture(archiveType, file("build/test.${archiveType}"))
        then:
        archive.assertContainsFile('file1.txt', 2)
        archive.assertContainsFile('file2.txt', 1)

        where:
        archiveType << ['tar', 'zip']
    }

    def "ensure duplicates can be excluded from #archiveType"() {
        given:
        createFilesStructureForDupeTests()
        buildFile << archiveTaskWithDuplicates(archiveType) << """
            archive {
                eachFile { it.duplicatesStrategy = 'exclude' }
            }
        """
        when:
        run 'archive'

        then:
        def archive = archiveFixture(archiveType, file("build/test.${archiveType}"))
        archive.assertContainsFile('file1.txt')
        archive.assertContainsFile('file2.txt')
        archive.content("file1.txt") == "dir1/file1.txt"

        where:
        archiveType << ['tar', 'zip']
    }

    def "renamed file will be treated as duplicate in #archiveType"() {
        given:
        createFilesStructureForDupeTests()
        buildFile << """
                task archive(type: ${archiveType.capitalize()}) {
                    from 'dir1'
                    from 'dir2'
                    destinationDirectory = buildDir
                    rename 'file2.txt', 'file1.txt'
                    archiveFileName = 'test.${archiveType}'
                    eachFile { it.duplicatesStrategy = 'exclude' }
                }
                """
        when:
        run 'archive'

        then:
        def archive = archiveFixture(archiveType, file("build/test.${archiveType}"))
        archive.hasDescendants('file1.txt')
        archive.assertFileContent('file1.txt', "dir1/file1.txt")

        where:
        archiveType << ['tar', 'zip']
    }

    def "ensure that the archiveFile can be used as an input to another task"() {
        given:
        createDir('dir1', {
            file('file1.txt').text = "dir1/file1.txt"
        })
        buildFile << """
        class TaskWithAutomaticDependency extends DefaultTask {
            @InputFile
            final RegularFileProperty inputFile = project.objects.fileProperty()

            @TaskAction
            void doNothing() {
                // does nothing
            }
        }

        task tar(type: Tar) {
            from 'dir1'
            archiveBaseName = "test"
            destinationDirectory.set(layout.buildDirectory)
        }
        task shouldRun(type: TaskWithAutomaticDependency) {
            // "Look Ma, no dependsOn!"
            inputFile.set(tar.archiveFile)
        }
        """
        when:
        run "shouldRun"
        then:
        executed(":tar")
    }

    @Issue("https://github.com/gradle/gradle#1108")
    def "can copy files into a different root with includeEmptyDirs=#includeEmptyDirs"() {
        given:
        createZip("test.zip") {
            dir1 {
                file "file1.txt"
            }
            dir2 {
                file "file2.txt"
                file "file3.txt"
                dir3 {}
            }
        }

        and:
        buildFile << """
            task copy(type: Copy) {
                from(zipTree("test.zip")) {
                    include "dir2/**"
                    includeEmptyDirs = ${includeEmptyDirs}
                    eachFile { fcd ->
                        fcd.relativePath = new RelativePath(!fcd.isDirectory(), fcd.relativePath.segments.drop(1))
                    }
                }
                into file("build/output")
            }
        """

        when:
        run "copy"

        then:
        file("build/output").assertHasDescendants(expectedDescendants)

        where:
        includeEmptyDirs | expectedDescendants
        true             | ["file2.txt", "file3.txt", "dir2/dir3"] // dir3 is not renamed as eachFile() does not apply to directories
        false            | ["file2.txt", "file3.txt"]
    }

    def "zipTree tracks task dependencies"() {
        given:
        buildFile """
            plugins {
                id('java-library')
            }

            task unpackJar(type: Copy) {
                from zipTree(jar.archiveFile)
                into 'build/unzippedJar'
            }
        """
        file("src/main/java/Hello.java") << """public class Hello {}"""

        when:
        run 'unpackJar'

        then:
        executedAndNotSkipped ':jar', ':unpackJar'
    }

    private def createTar(String name, Closure cl) {
        TestFile tarRoot = file("${name}.root")
        tarRoot.deleteDir()
        TestFile tar = file(name)
        tar.delete()
        tarRoot.create(cl)
        tarRoot.tarTo(tar)
    }

    private def createFilesStructureForDupeTests() {
        createDir('dir1', {
            file('file1.txt').text = "dir1/file1.txt"
        })
        createDir('dir2', {
            file 'file2.txt'
        })
        createDir('dir3', {
            file('file1.txt').text = "dir3/file1.txt"
        })
    }

    private static ArchiveTestFixture archiveFixture(String archiveType, TestFile archiveFile) {
        archiveType == 'tar'
            ? new TarTestFixture(archiveFile)
            : new ZipTestFixture(archiveFile)
    }

    private static String archiveTaskWithDuplicates(String archiveType) {
        def taskType = archiveType.capitalize()
        """
            task archive(type: ${taskType}) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDirectory = buildDir
                archiveFileName = 'test.${archiveType}'
            }
        """
    }
}
