/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.caching.internal.packaging

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import spock.lang.Issue

class BuildCacheEntryPackingIntegrationTest extends DaemonIntegrationSpec implements DirectoryBuildCacheFixture {

    private static final NON_ASCII_NAME = [
        "ascii-only": "ascii",
        "space": " ",
        "zwnj": "\u200c",
        "chinese": "敏捷的棕色狐狸跳过了懒狗",
        "cyrillic": "здравствуйте",
        "hungarian": "Árvíztűrő tükörfúrógép",
    ].values().join("-")

    private static final DEFAULT_ENCODINGS = [
        "UTF-8",
        "ISO-8859-1",
        "windows-1250",
    ]

    @Issue("https://github.com/gradle/gradle/issues/9877")
    def "can store and load files having non-ascii characters in file name when default file encoding is set to #fileEncoding"() {
        def fileName = NON_ASCII_NAME + ".txt"
        def outputFile = file("dir", fileName)

        buildFile << """
            task printCharsetProperties {
                doLast {
                    println "> Default charset: \${java.nio.charset.Charset.defaultCharset()}"
                    println "> Storing file in cache: $fileName"
                }
            }

            task createFile {
                dependsOn printCharsetProperties
                outputs.dir("dir")
                outputs.cacheIf { true }
                doLast {
                    file("dir/$fileName").text = "output"
                }
            }
        """

        when:
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding")
        then:
        output.contains("> Default charset: $fileEncoding")
        executedAndNotSkipped(":createFile")

        when:
        assert outputFile.delete()
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding")
        skipped(":createFile")

        then:
        output.contains("> Default charset: $fileEncoding")
        outputFile.text == "output"

        where:
        fileEncoding << DEFAULT_ENCODINGS
    }

    def "can store and load files having non-ascii characters in property name when default file encoding is set to #fileEncoding"() {
        def outputFile = file("output.txt")

        buildFile << """
            task printCharsetProperties {
                doLast {
                    println "> Default charset: \${java.nio.charset.Charset.defaultCharset()}"
                    println "> Storing with property name: $NON_ASCII_NAME"
                }
            }

            task createFile {
                dependsOn printCharsetProperties
                outputs.file("output.txt")
                    .withPropertyName("$NON_ASCII_NAME")
                outputs.cacheIf { true }
                doLast {
                    file("output.txt").text = "output"
                }
            }
        """

        when:
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding")
        then:
        output.contains("> Default charset: $fileEncoding")
        executedAndNotSkipped(":createFile")

        when:
        assert outputFile.delete()
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding")
        skipped(":createFile")

        then:
        output.contains("> Default charset: $fileEncoding")
        outputFile.text == "output"

        where:
        fileEncoding << DEFAULT_ENCODINGS
    }
}
