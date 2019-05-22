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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.hamcrest.CoreMatchers
import org.junit.Assume
import spock.lang.Issue
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.equalTo

@TestReproducibleArchives
class ArchiveIntegrationTest extends AbstractIntegrationSpec {

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
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
    }

    def cannotCreateAnEmptyTar() {
        given:
        buildFile << """
            task tar(type: Tar) {
                from 'test'
                destinationDir = buildDir
                archiveName = 'test.tar'
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
        file('dest').assertHasDescendants('subdir1/file1.txt', 'subdir2/file2.txt')
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

    def "allows user to provide a custom resource for the tarTree"() {
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
        run 'copy'
        then:
        file('dest').assertHasDescendants('someDir/1.txt')
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
                destinationDir = buildDir

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

    def "tarTreeFailsGracefully"() {
        given:
        file('content/some-file.txt').text = "Content"
        file('content').zipTo(file('compressedTarWithWrongExtension.tar'))
        buildFile << '''
            task copy(type: Copy) {
                from tarTree('compressedTarWithWrongExtension.tar')
                into 'dest'
            }
        '''.stripIndent()

        when:
        def failure = runAndFail('copy')

        then:
        failure.assertHasDescription("Execution failed for task ':copy'.")
        failure.assertThatCause(CoreMatchers.startsWith("Unable to expand TAR"))
    }

    def cannotCreateAnEmptyZip() {
        given:
        buildFile << '''
            task zip(type: Zip) {
                from 'test'
                destinationDir = buildDir
                archiveName = 'test.zip'
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
                destinationDir = buildDir
                archiveName = 'test.zip'
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
            'scripts/dir2/script.sh',
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
                destinationDir = buildDir
                archiveName = 'uncompressedTest.zip'
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
                destinationDir = buildDir
                archiveName = 'compressedTest.zip'
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
            'scripts/dir2/script.sh')

        expandDir.file('prefix/dir1/file1.txt').assertContents(equalTo(randomAscii))

        def expandCompressedDir = file('expandedCompressed')
        file('build/compressedTest.zip').unzipTo(expandCompressedDir)
        expandCompressedDir.assertHasDescendants(
            'prefix/dir1/file1.txt',
            'prefix/file1.txt',
            'prefix/dir2/file2.txt',
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
                destinationDir = buildDir
                archiveName = 'test.tar'
            }
'''
        when:
        run 'tar'
        then:
        def expandDir = file('expanded')
        file('build/test.tar').untarTo(expandDir)
        expandDir.assertHasDescendants('dir1/file1.txt', 'file1.txt', 'dir2/file2.txt', 'scripts/dir2/script.sh')

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
                destinationDir = buildDir
                archiveName = 'test.tgz'
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
                destinationDir = buildDir
                archiveName = 'test.tbz2'
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
                destinationDir = file('build')
                archiveName = 'test.zip'
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
                destinationDir = file('build')
                archiveName = 'test.zip'
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
            destinationDir = buildDir
            archiveName = 'test.zip'
        }
        '''
        when:
        run 'zip'
        then:
        def expandDir = file('expanded')
        file('build/test.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('shared/zip.txt', 'zipdir1/file1.txt', 'shared/tar.txt', 'tardir1/file1.txt', 'shared/dir.txt', 'dir1/file1.txt')
    }


    def ensureDuplicatesIncludedInTarByDefault() {
        given:
        createFilesStructureForDupeTests()
        buildFile << '''
            task tar(type: Tar) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.tar'
            }
            '''
        when:
        run 'tar'

        then:
        def tar = new TarTestFixture(file("build/test.tar"))
        tar.assertContainsFile('file1.txt', 2)
        tar.assertContainsFile('file2.txt')
    }

    def ensureDuplicatesCanBeExcludedFromTar() {
        given:
        createFilesStructureForDupeTests()
        buildFile << '''
            task tar(type: Tar) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.tar'
                eachFile { it.duplicatesStrategy = 'exclude' }
            }
            '''
        when:
        run 'tar'

        then:
        def tar = new TarTestFixture(file("build/test.tar"))
        tar.assertContainsFile('file1.txt')
        tar.assertContainsFile('file2.txt')
        tar.content("file1.txt") == "dir1/file1.txt"
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
            baseName = "test"
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
        ":tar" in executedTasks
    }

    @Issue("https://github.com/gradle/gradle#1108")
    @Unroll
    def "can copy files into a different root with includeEmptyDirs=#includeEmptyDirs"() {
        Assume.assumeFalse("This test case is not implemented when includeEmptyDirs=true", includeEmptyDirs)

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
                into buildDir
            }
        """

        when:
        run "copy"

        then:
        file("build").assertHasDescendants(expectedDescendants)

        where:
        includeEmptyDirs | expectedDescendants
        true             | ["file2.txt", "file3.txt", "dir3"]
        false            | ["file2.txt", "file3.txt"]
    }

    private def createTar(String name, Closure cl) {
        TestFile tarRoot = file("${name}.root")
        TestFile tar = file(name)
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
}
