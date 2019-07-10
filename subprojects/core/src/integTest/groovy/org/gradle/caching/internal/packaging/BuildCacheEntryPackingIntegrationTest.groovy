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
import spock.lang.Unroll

class BuildCacheEntryPackingIntegrationTest extends DaemonIntegrationSpec implements DirectoryBuildCacheFixture {
    @Issue("https://github.com/gradle/gradle/issues/9877")
    @Unroll
    def "can store and load files having non-ascii characters in file name and property name using default file encoding #fileEncoding"() {
        def name = [
            "ascii-only": "ascii",
            "space": " ",
            "zwnj": "\u200c",
            "chinese": "敏捷的棕色狐狸跳过了懒狗",
            "cyrillic": "здравствуйте",
            "hungarian": "Árvíztűrő tükörfúrógép",
        ].values().join("-")
        def fileName = name + ".txt"
        def outputFile = file("dir", fileName)

        buildFile << """
            println "> Default charset: \${java.nio.charset.Charset.defaultCharset()}"
            println "> Storing file in cache: $fileName"

            task createFile {
                outputs.dir("dir")
                    .withPropertyName("$name")
                outputs.cacheIf { true }
                doLast {
                    file("dir/$fileName").text = "output"
                }
            }
        """

        when:
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding", "--info")
        then:
        output.contains("> Default charset: $fileEncoding")
        executedAndNotSkipped(":createFile")

        when:
        assert outputFile.delete()
        withBuildCache().run("createFile", "-Dfile.encoding=$fileEncoding", "--info")
        skipped(":createFile")

        then:
        output.contains("> Default charset: $fileEncoding")
        outputFile.text == "output"

        where:
        fileEncoding << ["UTF-8", "ISO-8859-1", "windows-1250"]
    }
}
