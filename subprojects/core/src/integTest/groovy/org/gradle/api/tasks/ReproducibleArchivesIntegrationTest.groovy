/*
 * Copyright 2016 the original author or authors.
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

import org.apache.commons.io.FilenameUtils
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.ArchiveTestFixture
import org.gradle.test.fixtures.archive.TarTestFixture
import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ReproducibleArchivesIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/8051")
    def "reproducible #taskName for directory - #files"() {
        given:
        files.each {
            file("src/${it}").text = it
        }
        buildFile << """
            task ${taskName}(type: ${taskType}) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                from 'src'
                destinationDirectory = buildDir
                archiveFileName = 'test.${fileExtension}'
                filePermissions {}
                dirPermissions {}
            }
            """

        when:
        succeeds taskName

        then:
        file("build/test.${fileExtension}").md5Hash == expectedHash

        where:
        input << [
            ['DIR1/FILE11.txt', 'dir2/file22.txt', 'DIR3/file33.txt'].permutations(),
            ['zip', 'tar']
        ].combinations()
        files = input[0]
        taskName = input[1]
        taskType = taskName.capitalize()
        fileExtension = taskName
        expectedHash = taskName == 'tar' ? 'e700ead57290d37d0950a9c87689e6e4' : '002ed122f6f71124e244151251037162'
    }

    def "timestamps are ignored in #taskName"() {
        given:
        createTestFiles()
        buildFile << """
            task ${taskName}(type: ${taskType}) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                from 'dir1'
                destinationDirectory = buildDir
                archiveFileName = 'test.${fileExtension}'
                filePermissions {}
                dirPermissions {}
            }
            """

        when:
        succeeds taskName

        def archive = file("build/test.${fileExtension}")
        then:
        def firstFileHash = archive.md5Hash

        when:
        file('dir1/file11.txt').makeOlder()
        archive.delete()
        succeeds taskName

        then:
        archive.md5Hash == firstFileHash

        where:
        taskName << ['tar', 'zip']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#compression compressed tar files are reproducible"() {
        given:
        createTestFiles()
        buildFile << """
            task tar(type: Tar) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                compression = '${compression}'
                from 'dir1', 'dir2', 'dir3'
                destinationDirectory = buildDir
                archiveFileName = 'test.tar.${compression}'
                filePermissions {}
                dirPermissions {}
            }
            """

        when:
        succeeds 'tar'

        then:
        file("build/test.tar.${compression}").md5Hash == md5

        // Reason for different gzip checksum on JDK16: https://jdk.java.net/16/release-notes#JDK-8244706
        where:
        compression | md5
        'gzip'      | (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16) ? '022ce6c9bfb4705481fafdbe0d3c0334' : '7b86e679a3c6cda52736e1f167cc04f5')
        'bzip2'     | '54615d3194655da3f7f72c8859f66fa5'
    }

    def "#taskName preserves order of child specs"() {
        given:
        createTestFiles()
        buildFile << """
            task ${taskName}(type: ${taskType}) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                from('dir2') {
                    into 'dir2'
                }
                from('dir1') {
                    into 'dir1'
                }
                from 'dir1/file13.txt'
                from 'dir1/file11.txt'
                destinationDirectory = buildDir
                archiveFileName = 'test.${fileExtension}'
                filePermissions {}
                dirPermissions {}
            }
        """

        when:
        succeeds taskName

        then:
        archive(file("build/test.${fileExtension}")).hasDescendantsInOrder(
            'file13.txt',
            'file11.txt',
            'dir2/file21.txt',
            'dir2/file22.txt',
            'dir2/file23.txt',
            'dir2/file24.txt',
            'dir1/file11.txt',
            'dir1/file12.txt',
            'dir1/file13.txt',
            'dir1/file14.txt')

        where:
        taskName << ['tar', 'zip']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#taskName can use zipTree and tarTree"() {
        given:
        createTestFiles()
        buildFile << """
            task aTar(type: Tar) {
                reproducibleFileOrder = true
                from('dir1')
                destinationDirectory = buildDir
                archiveFileName = 'test.tar'
                filePermissions {}
                dirPermissions {}
            }
            task aZip(type: Zip) {
                reproducibleFileOrder = true
                from('dir2')
                destinationDirectory = buildDir
                archiveFileName = 'test.zip'
                filePermissions {}
                dirPermissions {}
            }

            task ${taskName}(type: ${taskType}) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                destinationDirectory = buildDir
                archiveFileName = 'combined.${fileExtension}'

                from zipTree(aZip.archiveFile)
                from tarTree(aTar.archiveFile)

                dependsOn aZip, aTar

                filePermissions {}
                dirPermissions {}
            }
        """

        when:
        succeeds taskName

        then:
        archive(file("build/combined.${fileExtension}")).hasDescendantsInOrder(
            'file21.txt',
            'file22.txt',
            'file23.txt',
            'file24.txt',
            'file11.txt',
            'file12.txt',
            'file13.txt',
            'file14.txt')

        where:
        taskName << ['zip', 'tar']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#taskName uses only first duplicate"() {
        given:
        duplicateEntriesInArchive(taskName, taskType, fileExtension)

        buildFile << """
            ${taskName} {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        """

        when:
        succeeds taskName

        then:
        archive(file("build/test.${fileExtension}")).content('test.txt') == "from dir2"

        where:
        taskName << ['zip', 'tar']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#taskName can fail for duplicate entries"() {
        given:
        duplicateEntriesInArchive(taskName, taskType, fileExtension)

        buildFile << """
            ${taskName} {
                duplicatesStrategy = DuplicatesStrategy.FAIL
            }
        """

        when:
        fails taskName

        then:
        failure.assertHasCause("Cannot copy file '${file('dir1/test.txt')}' to 'test.txt' because file '${file('dir2/test.txt')}' has already been copied there.")

        where:
        taskName << ['zip', 'tar']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#taskName supports filtered entries"() {

        given:
        file('dir1/test.txt').text = "Hello"
        buildFile << """
        task ${taskName}(type: ${taskType}) {
            reproducibleFileOrder = true
            preserveFileTimestamps = false
            destinationDirectory = buildDir
            archiveFileName = 'test.${fileExtension}'

            from('dir1') {
                filter { 'Goodbye' }
            }
        }
        """

        when:
        succeeds taskName

        then:
        archive(file("build/test.${fileExtension}")).content('test.txt') == 'Goodbye'

        where:
        taskName << ['zip', 'tar']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    def "#taskName sorts by target file name"() {

        given:
        createDir('dir1') {
            file('test1.txt') << 'test1'
            file('test2.txt') << 'test2'
        }
        buildFile << """
        task ${taskName}(type: ${taskType}) {
            reproducibleFileOrder = true
            preserveFileTimestamps = false
            destinationDirectory = buildDir
            archiveFileName = 'test.${fileExtension}'

            from('dir1') {
                rename { it == 'test1.txt' ? 'test4.txt' : 'test3.txt' }
            }
        }
        """

        when:
        succeeds taskName

        then:
        def archiveFile = archive(file("build/test.${fileExtension}"))
        archiveFile.hasDescendants('test3.txt', 'test4.txt')
        archiveFile.content('test3.txt') == 'test2'
        archiveFile.content('test4.txt') == 'test1'

        where:
        taskName << ['zip', 'tar']
        taskType = taskName.capitalize()
        fileExtension = taskName
    }

    private void duplicateEntriesInArchive(taskName, taskType, fileExtension) {
        file('dir1/test.txt') << "from dir1"
        file('dir2/test.txt') << "from dir2"

        buildFile << """
            task ${taskName}(type: ${taskType}) {
                reproducibleFileOrder = true
                preserveFileTimestamps = false
                destinationDirectory = buildDir
                archiveFileName = 'test.${fileExtension}'

                from 'dir2'
                from 'dir1'
            }
        """
    }

    ArchiveTestFixture archive(TestFile archiveFile) {
        String type = FilenameUtils.getExtension(archiveFile.name)
        if (type == 'zip') {
            new ZipTestFixture(archiveFile)
        } else {
            new TarTestFixture(archiveFile)
        }
    }

    def createTestFiles() {
        createDir('dir1') {
            file('file12.txt') << 'test2'
            file('file11.txt') << 'test1'
            file('file13.txt') << 'test3'
            file('file14.txt') << 'test4'
        }
        createDir('dir2') {
            file('file22.txt') << 'test2'
            file('file21.txt') << 'test1'
            file('file23.txt') << 'test3'
            file('file24.txt') << 'test4'
        }
        createDir('dir3') {
            file('file32.txt') << 'test2'
            file('file31.txt') << 'test1'
            file('file33.txt') << 'test3'
            file('file34.txt') << 'test4'
        }
    }
}
