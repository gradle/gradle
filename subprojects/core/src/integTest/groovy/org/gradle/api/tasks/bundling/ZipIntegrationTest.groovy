/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.hash.Funnels
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.ZipTestFixture
import spock.lang.Issue
import spock.lang.Unroll

import java.nio.charset.Charset

class ZipIntegrationTest extends AbstractIntegrationSpec {

    def ensureDuplicatesIncludedWithoutWarning() {
        given:
        createTestFiles()
        buildFile << '''
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
            '''
        when:
        run 'zip'

        then:
        def theZip = new ZipTestFixture(file('build/test.zip'))
        theZip.hasDescendants('file1.txt', 'file1.txt', 'file2.txt')
    }

    def ensureDuplicatesCanBeExcluded() {
        given:
        createTestFiles()
        buildFile << '''
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
                eachFile { it.duplicatesStrategy = 'exclude' }
            }
            '''
        when:
        run 'zip'

        then:
        def theZip = new ZipTestFixture(file('build/test.zip'))
        theZip.hasDescendants('file1.txt', 'file2.txt')
    }

    def renamedFileWillBeTreatedAsDuplicateZip() {
        given:
        createTestFiles()
        buildFile << '''
                task zip(type: Zip) {
                    from 'dir1'
                    from 'dir2'
                    destinationDir = buildDir
                    rename 'file2.txt', 'file1.txt'
                    archiveName = 'test.zip'
                    eachFile { it.duplicatesStrategy = 'exclude' }
                }
                '''
        when:
        run 'zip'

        then:
        def theZip = new ZipTestFixture(file('build/test.zip'))
        theZip.hasDescendants('file1.txt')
        theZip.assertFileContent('file1.txt', "dir1/file1.txt")
    }

    def zip64Support() {
        given:
        createTestFiles()
        buildFile << '''
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                destinationDir = buildDir
                archiveName = 'test.zip'
                zip64 = true
            }
            '''
        when:
        run 'zip'

        then:
        def theZip = new ZipTestFixture(file('build/test.zip'))
        theZip.hasDescendants('file1.txt', 'file2.txt')
    }

    @Unroll
    def "can create Zip file with #metadataCharset metadata charset"() {
        given:
        createTestFilesWithEncoding(filename, metadataCharset)
        buildFile << """
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
                metadataCharset = '$metadataCharset'
            }
            """

        when:
        succeeds 'zip'

        then:
        def theZip = new ZipTestFixture(file('build/test.zip'), metadataCharset)
        theZip.hasDescendants("${filename}1.txt", "${filename}2.txt", "${filename}3.txt")

        where:
        metadataCharset                 | filename
        Charset.defaultCharset().name() | 'file'
        'UTF-8'                         | '中文'
        'ISO-8859-1'                    | 'ÈÇ'
    }

    def "can convert metadata to ASCII"() {
        given:
        def filename = 'file-éö'
        createTestFilesWithEncoding(filename, 'ISO-8859-1')
        buildFile << """
            task zip(type: Zip) {
                from 'dir1'
                from 'dir2'
                from 'dir3'
                destinationDir = buildDir
                archiveName = 'test.zip'
                metadataCharset = 'US-ASCII'
            }
            """

        when:
        succeeds 'zip'

        then:
        def garbledFileName = "file-%U00E9%U00F6"
        def theZip = new ZipTestFixture(file('build/test.zip'), 'UTF-8')
        theZip.hasDescendants("${garbledFileName}1.txt", "${garbledFileName}2.txt", "${garbledFileName}3.txt")
    }

    def "order of files is not important"() {
        given:
        createTestFiles()
        buildFile << """
            task zip(type: Zip) {
                from files(${files.collect {"'${it}'"}.join(',')})
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
            """

        when:
        succeeds 'zip'

        then:
        fileHash('build/test.zip') == 'd2ba5ed628630c1fa24cf1eda494af3f'

        where:
        files << ['dir1/file1.txt', 'dir2/file2.txt', 'dir3/file3.txt'].permutations()

    }

    def "timestamps are ignored for reproducible jars"() {
        given:
        createTestFiles()
        buildFile << """
            task zip(type: Zip) {
                from 'dir1'
                destinationDir = buildDir
                archiveName = 'test.zip'
            }
            """

        when:
        succeeds 'zip'

        then:
        def firstFileHash = fileHash('build/test.zip')

        when:
        file('dir1/file1.txt').makeOlder()
        file('build/test.zip').delete()
        succeeds 'zip'

        then:
        fileHash('build/test.zip') == firstFileHash
    }

    private String fileHash(String fileName) {
        def hasher = Hashing.md5().newHasher()
        Files.copy(file(fileName), Funnels.asOutputStream(hasher))
        hasher.hash().toString()
    }

    @Unroll
    def "reports error for #metadataCharset metadata charset"() {
        given:
        createTestFiles()
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            task zip(type: Zip) {
                from 'dir1'
                destinationDir = buildDir
                archiveName = 'test.zip'
                metadataCharset = $metadataCharset
            }
            """

        when:
        fails 'zip'

        then:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause(cause)

        where:
        metadataCharset | cause
        "'UNSUPPORTED'" | "Charset for metadataCharset 'UNSUPPORTED' is not supported by your JVM"
        null            | "metadataCharset must not be null"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-1346")
    def "task is out of date after `into` changes"() {
        file("src/main/java/Main.java") << "public class Main {}"
        buildFile << """
            apply plugin: "java"

            task zip(type: Zip) {
                into('src') {
                    into('data') {
                        from sourceSets.main.java
                    }
                }
            }
        """

        when:
        succeeds "zip"
        then:
        skippedTasks.empty

        when:
        succeeds "zip"
        then:
        skippedTasks as List == [":zip"]

        buildFile.delete()
        buildFile << """
            apply plugin: "java"

            task zip(type: Zip) {
                into('sources') {
                    into('data') {
                        from sourceSets.main.java
                    }
                }
            }
        """

        when:
        succeeds "zip", "--info"
        then:
        skippedTasks.empty
        output.contains "Value of input property 'rootSpec\$1\$1.destPath' has changed for task ':zip'"
        output.contains "Value of input property 'rootSpec\$1\$1\$1.destPath' has changed for task ':zip'"
    }

    private def createTestFiles() {
        createDir('dir1', {
            file('file1.txt').text = "dir1/file1.txt"
        })
        createDir('dir2', {
            file('file2.txt').text = "dir2/file2.txt"
        })
        createDir('dir3', {
            file('file1.txt').text = "dir3/file1.txt"
        })
    }

    private def createTestFilesWithEncoding(String filename, String encoding) {
        String encodedName = filename
        if (encoding != null) {
            encodedName = new String(filename.getBytes(encoding), encoding)
        }
        (1..3).each { idx ->
            createDir("dir$idx", {
                file("${encodedName}${idx}.txt").text = "dir$idx/${encodedName}${idx}.txt"
            })
        }
    }

}
