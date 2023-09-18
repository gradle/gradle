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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class GitIgnoreGeneratorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    InitSettings settings = Mock()
    File gitignoreFile = tmpDir.file(".gitignore")

    def setup() {
        Directory target = Mock()
        RegularFile ignoreFile = Mock()
        1 * settings.target >> target
        1 * target.file('.gitignore') >> ignoreFile
        1 * ignoreFile.asFile >> gitignoreFile
    }

    def "generates .gitignore file"() {
        setup:
        def generator = new GitIgnoreGenerator()

        when:
        generator.generate(settings)

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("${getGeneratedGitignoreContent()}")
    }

    def "appends .gitignore file if it already exists"() {
        setup:
        def generator = new GitIgnoreGenerator()
        gitignoreFile << 'ignoredFolder/'

        when:
        generator.generate(settings)

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("""ignoredFolder/
${getGeneratedGitignoreContent()}""")
    }

    def "avoid adding duplicated entries when .gitignore file already exists [#entry]"() {
        setup:
        def generator = new GitIgnoreGenerator()
        gitignoreFile << entry

        when:
        generator.generate(settings)

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("""$entry
${getGeneratedGitignoreContent(entry)}""")

        where:
        entry << ['.gradle', 'build']
    }

    private static String getGeneratedGitignoreContent(String excludingEntry = null) {
        def builder = new StringBuilder()

        if (excludingEntry != '.gradle') {
            builder << '''# Ignore Gradle project-specific cache directory
.gradle
'''
        }

        if (excludingEntry != 'build') {
            if (builder.length() > 0) {
                builder << '\n'
            }
            builder << '''# Ignore Gradle build output directory
build
'''
        }

        return builder.toString()
    }
}
