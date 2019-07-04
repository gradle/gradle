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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import spock.lang.Issue
import spock.lang.Unroll

class BuildCacheEntryPackingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    @Issue("https://github.com/gradle/gradle/issues/9877")
    @Unroll
    def "can store and load files having #type characters in name (default file encoding #fileEncoding)"() {
        def outputFile = file(fileName)

        executer.beforeExecute {
            executer.requireDaemon()
                .requireIsolatedDaemons()
                .useOnlyRequestedJvmOpts()
                .withBuildJvmOpts("-Dfile.encoding=$fileEncoding")
        }

        buildFile << """
            println "> File encoding: \${System.getProperty("file.encoding")}"
            println "> Default charset: \${java.nio.charset.Charset.defaultCharset()}"

            task createFile {
                outputs.file("$fileName")
                outputs.cacheIf { true }
                doLast {
                    file("$fileName").text = "output"
                }
            }
        """

        when:
        withBuildCache().run"createFile", "--info"
        then:
        output.contains("> Default charset: $fileEncoding")
        executedAndNotSkipped(":createFile")

        when:
        assert outputFile.delete()
        withBuildCache().run"createFile", "--info"

        then:
        output.contains("> Default charset: $fileEncoding")
        outputFile.text == "output"

        where:
        [type, fileName, fileEncoding] << [
            [
                "ascii-only": "input-file.txt",
                "chinese": "输入文件.txt",
                "cyrillic": "здравствуйте.txt",
                "hungarian": "Dezső.txt",
                "space": "input file.txt",
                "zwnj": "input\u200cfile.txt",
                "url-quoted": "input%<file>#2.txt",
            ].entrySet(),
            [
                "UTF-8",
                "ISO-8859-1",
                "ISO-8859-2",
                "ISO-8859-5",
                "GBK"
            ]
        ].combinations { text, encoding -> [text.key, text.value, encoding] }
    }
}
