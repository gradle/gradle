/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.tasks

import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.junit.Rule

import static org.hamcrest.Matchers.equalTo

public class ArchiveIntegrationTest extends AbstractIntegrationSpec {

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
        TestFile tar = file()
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

    def "allows user to provide a custom resource for the tarTree"() {
        given:
        TestFile tar = file()
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
        TestFile tar = file()
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

    def "knows compression of the tar"() {
        given:
        TestFile tar = file()
        tar.tbzTo(file('test.tbz2'))
        and:
        buildFile << '''
            task myTar(type: Tar) {
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
        TestFile tar = file()
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

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    def "tarTreeFailsGracefully"() {
        given:
        buildFile << '''
            task copy(type: Copy) {
                //the input file comes from the resources to make sure it is truly improper 'tar', see GRADLE-1952
                from tarTree('compressedTarWithWrongExtension.tar')
                into 'dest'
            }
'''
        when:
        def failure = runAndFail('copy')
        then:
        assert failure.error.contains("Unable to expand TAR")
        assert failure.error.contains("compression based on the file extension")
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
                'scripts/dir2/script.sh')

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
        createFilesStructureForDupeTests();
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
