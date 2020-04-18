/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file

import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.Zip
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import static org.junit.Assert.assertEquals

class ZipTreeIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "can unzip Zip file that has #nonAsciiChars encoded using #metadataCharset"() {
        given:
        def testFile = createTestFiles(nonAsciiChars, metadataCharset)
        buildFile << """
            task unzip(type: Copy) {
                from(zipTree(file: '${testFile.getAbsolutePath()}', metadataCharset: '$metadataCharset'))
                into 'dest'
            }
            """

        when:
        succeeds 'unzip'

        then:
        file('dest').assertHasDescendants(
            "${nonAsciiChars}/file${nonAsciiChars}1.txt",
            "${nonAsciiChars}/file${nonAsciiChars}2.txt",
            "${nonAsciiChars}/file${nonAsciiChars}3.txt"
        )

        where:
        metadataCharset | nonAsciiChars
        'UTF-8'         | 'ÄÖÜ'
        'CP437'         | 'ÄÖÜ'
        'UTF-8'         | 'екс'
        'KOI8-R'        | 'екс'
    }

    @Unroll
    def "test produces correct zip file with #nonAsciiChars encoded using #metadataCharset"() {
        given:
        def testFile = createTestFiles(nonAsciiChars, metadataCharset)

        expect:
        new FileInputStream(testFile).with { inputReader ->
            inputReader.skip(30)

            encodedBytes.eachWithIndex { expectedByte, index ->
                assertEquals("wrong value at index $index", expectedByte, inputReader.read())
            }
        }

        where:
        metadataCharset | nonAsciiChars || encodedBytes
        'UTF-8'         | 'ÄÖÜ'         || [0xC3, 0x84, 0xC3, 0x96, 0xC3, 0x9C]
        'CP437'         | 'ÄÖÜ'         || [0x8E, 0x99, 0x9A]
        'UTF-8'         | 'екс'         || [0xD0, 0xB5, 0xD0, 0xBA, 0xD1, 0x81]
        'KOI8-R'        | 'екс'         || [0xC5, 0xCB, 0xD3]
    }

    private def createTestFiles(String nonAsciiChars, String metadataCharset) {
        TestFile inputDir = createDir("input", {
            createDir("input/${nonAsciiChars}", {
                file("file${nonAsciiChars}1.txt").text = "dir/file1.txt"
                file("file${nonAsciiChars}2.txt").text = "dir/file2.txt"
                file("file${nonAsciiChars}3.txt").text = "dir/file3.txt"
            })
        })

        Zip zip = new Zip()
        zip.setWhenempty((Zip.WhenEmpty) Zip.WhenEmpty.getInstance(Zip.WhenEmpty.class, "create"))
        TestFile zipFile = file("output.zip")
        zip.setDestFile(zipFile)
        zip.setEncoding(metadataCharset)
        zip.setBasedir(inputDir)
        zip.setProject(new Project())
        zip.execute()

        return zipFile
    }
}
