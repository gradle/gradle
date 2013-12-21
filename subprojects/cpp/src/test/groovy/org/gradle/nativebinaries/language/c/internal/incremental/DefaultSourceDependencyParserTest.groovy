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

class DefaultSourceDependencyParserTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def testDirectory = temporaryFolder.testDirectory
    def sourceDirectory = testDirectory.createDir("sources")
    def quotedIncludes = []
    def systemIncludes = []
    def includesParser = Mock(IncludesParser)
    def includePaths = []

    def setup() {
        def includes = Mock(IncludesParser.Includes)
        includesParser.parseIncludes(sourceFile) >> includes
        includes.getQuotedIncludes() >> quotedIncludes
        includes.getSystemIncludes() >> systemIncludes
    }

    protected TestFile getSourceFile() {
        sourceDirectory.file('source.c')
    }

    def getDependencies() {
        return new DefaultSourceDependencyParser(includesParser, includePaths).parseDependencies(sourceFile)
    }

    def "handles source file with no includes"() {
        expect:
        dependencies == []
    }

    def "ignores include files that do not exist"() {
        when:
        quotedIncludes << "test.h"
        systemIncludes << "system"

        then:
        dependencies == []
    }

    def "locates quoted includes in same directory"() {
        when:
        final header1 = sourceDirectory.createFile("test1.h")
        final header2 = sourceDirectory.createFile("test2.h")

        and:
        quotedIncludes << "test1.h" << "test2.h"

        then:
        dependencies == [header1, header2]
    }

    def "locates quoted includes relative to source directory"() {
        when:
        final header1 = sourceDirectory.createFile("test1.h")
        final header2 = sourceDirectory.file("nested", "test2.h").createFile()
        final header3 = sourceDirectory.file("..", "sibling", "test3.h").createFile()

        and:
        quotedIncludes << "test1.h" << "nested/test2.h" << "../sibling/test3.h"

        then:
        dependencies == [header1, header2, header3]
    }

    def "does not locate system includes in same directory"() {
        when:
        sourceDirectory.file("system.h").createFile()

        and:
        systemIncludes << "system.h"

        then:
        dependencies == []
    }

    def "locates includes in path"() {
        when:
        def includeDir1 = testDirectory.file("include1")
        final header11 = includeDir1.file("test11.h").createFile()
        final header12 = includeDir1.file("test12.h").createFile()
        def includeDir2 = testDirectory.file("include2")
        final header21 = includeDir1.file("test21.h").createFile()
        final header22 = includeDir1.file("test22.h").createFile()

        and:
        includePaths << includeDir1 << includeDir2
        quotedIncludes << "test11.h" << "test21.h"
        systemIncludes << "test12.h" << "test22.h"

        then:
        dependencies == [header11, header21, header12, header22]
    }

    def "searches relative before searching include path"() {
        when:
        final relativeHeader = sourceDirectory.createFile("test.h")
        final includeDir = testDirectory.file("include")
        includeDir.createFile("test.h")
        final otherHeader = includeDir.createFile("other.h")

        and:
        includePaths << includeDir
        quotedIncludes << "test.h" << "other.h"

        then:
        dependencies == [relativeHeader, otherHeader]
    }
}
