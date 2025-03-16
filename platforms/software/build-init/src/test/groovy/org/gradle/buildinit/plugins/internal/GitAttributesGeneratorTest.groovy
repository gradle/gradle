/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class GitAttributesGeneratorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    InitSettings settings = Mock(InitSettings) {
        isWithComments() >> true
    }
    File gitattributesFile = tmpDir.file(".gitattributes")

    def setup() {
        Directory target = Mock()
        RegularFile attributesFile = Mock()
        1 * settings.target >> target
        1 * target.file('.gitattributes') >> attributesFile
        1 * attributesFile.asFile >> gitattributesFile
    }

    def "generates .gitattributes file"() {
        setup:
        def generator = new GitAttributesGenerator()

        when:
        generator.generate(settings, null)

        then:
        gitattributesFile.file
        gitattributesFile.text == toPlatformLineSeparators("${getGeneratedGitattributesContent()}")
    }

    private static String getGeneratedGitattributesContent() {
        def builder = new StringBuilder()

        builder << '#\n'
        builder << '# https://help.github.com/articles/dealing-with-line-endings/\n'
        builder << '#\n'
        builder << '''# Linux start script should use lf
/gradlew        text eol=lf
'''
        builder << '\n'
        builder << '''# These are Windows script files and should use crlf
*.bat           text eol=crlf
'''
        builder << '\n'
        builder << '''# Binary files should be left untouched
*.jar           binary
'''
        builder << '\n'

        return builder.toString()
    }
}
