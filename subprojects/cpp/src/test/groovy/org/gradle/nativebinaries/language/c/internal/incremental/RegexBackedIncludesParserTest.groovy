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
package org.gradle.nativebinaries.language.c.internal.incremental

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class RegexBackedIncludesParserTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def parser = new RegexBackedIncludesParser()

    protected TestFile getSourceFile() {
        testDirectory.file('source.c')
    }

    TestFile getTestDirectory() {
        temporaryFolder.testDirectory
    }

    def getParsedIncludes() {
        parser.parseIncludes(sourceFile)
    }

    def getIncludes() {
        return parsedIncludes.quotedIncludes
    }

    def getSystemIncludes() {
        return parsedIncludes.systemIncludes
    }

    def "parses file with no includes"() {
        when:
        sourceFile << ""

        then:
        includes == []
        systemIncludes == []
    }

    def "finds quoted include"() {
        when:
        sourceFile << """
    #include "test.h"
"""

        then:
        includes == ["test.h"]
        systemIncludes == []
    }

    def "finds system include"() {
        when:
        sourceFile << """
    #include <test.h>
"""

        then:
        includes == []
        systemIncludes == ["test.h"]
    }

    def "finds multiple includes"() {
        when:
        sourceFile << """
    #include "test1"
    #include "test2"
    #include <system1>
    #include <system2>
"""
        then:
        includes == ["test1", "test2"]
        systemIncludes == ["system1", "system2"]
    }

    def "finds includes surrounded by different whitespace"() {
        when:
        sourceFile << """
    #include     "test1"
    #include \t "test2"  \t
  \t  #include \t "test3"
    #include     <system1>
    #include \t <system2>  \t
  \t  #include \t <system3>
"""
        then:
        includes == ["test1", "test2", "test3"]
        systemIncludes == ["system1", "system2", "system3"]
    }

    def "find quoted include with special characters"() {
        when:
        sourceFile << """
    #include "$included"
"""
        then:
        includes == [included]

        where:
        included << ["test'file", "testfile'", "'testfile'", "test<>file", "test>file", "<testFile>", "test<file", "test file"]
    }

    def "find system include with special characters"() {
        when:
        sourceFile << """
    #include <$included>
"""
        then:
        systemIncludes == [included]

        where:
        included << ["test'file", "testfile'", "'testfile'", "test<file", "test\"file", "\"testFile\"", "test file"]
    }
}
