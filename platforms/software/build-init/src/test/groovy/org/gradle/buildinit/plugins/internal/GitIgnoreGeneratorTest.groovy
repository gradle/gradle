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

    InitSettings settings = Mock(InitSettings) {
        isWithComments() >> true
    }
    File gitignoreFile = tmpDir.file(".gitignore")

    def setup() {
        Directory target = Mock()
        RegularFile ignoreFile = Mock()
        settings.target >> target
        target.file('.gitignore') >> ignoreFile
        ignoreFile.asFile >> gitignoreFile
    }

    def "generates .gitignore file"() {
        setup:
        def generator = new GitIgnoreGenerator()

        when:
        generator.generate(settings, null)

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("${getGeneratedGitignoreContent()}")
    }

    def "appends .gitignore file if it already exists"() {
        setup:
        def generator = new GitIgnoreGenerator()
        gitignoreFile << 'ignoredFolder/'

        when:
        generator.generate(settings, null)

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
        generator.generate(settings, null)

        then:
        gitignoreFile.file
        gitignoreFile.text == toPlatformLineSeparators("""$entry
${getGeneratedGitignoreContent(entry)}""")

        where:
        entry << ['.gradle', '.kotlin']
    }

    def "avoids adding duplicated build ignore block when .gitignore file already exists"() {
        setup:
        def generator = new GitIgnoreGenerator()
        gitignoreFile << '''build/
!**/docs/**/build/
!**/src/**/build/'''

        when:
        generator.generate(settings, null)

        then:
        gitignoreFile.text == toPlatformLineSeparators("""build/
!**/docs/**/build/
!**/src/**/build/
${getGeneratedGitignoreContent('build')}""")
    }

    def "does not change existing build ignore rules [#existingRules]"() {
        setup:
        def generator = new GitIgnoreGenerator()
        gitignoreFile << existingRules

        when:
        generator.generate(settings, null)

        then:
        gitignoreFile.text == toPlatformLineSeparators("""$existingRules
${getGeneratedGitignoreContent('build')}""")

        where:
        existingRules << [
            'build',
            'build/',
            'Build/',
            '''build
!vendor/build/''',
            '''build
src/generated/build/''',
            'src/generated/build/'
        ]
    }

    def "does not append entries when generated twice"() {
        setup:
        def generator = new GitIgnoreGenerator()

        when:
        generator.generate(settings, null)
        def contentAfterFirstGeneration = gitignoreFile.text
        generator.generate(settings, null)

        then:
        gitignoreFile.text == contentAfterFirstGeneration
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
            builder << '''# Ignore Gradle build output directories, except when used as part of source or documentation paths
build/
!**/docs/**/build/
!**/src/**/build/
'''
        }

        if (excludingEntry != '.kotlin') {
            if (builder.length() > 0) {
                builder << '\n'
            }
            builder << '''# Ignore Kotlin plugin data
.kotlin
'''
        }

        return builder.toString()
    }
}
