/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import spock.lang.Ignore

import java.util.regex.Pattern

import static org.gradle.internal.filewatch.ChangeReporter.SHOW_INDIVIDUAL_CHANGES_LIMIT
import static org.gradle.util.TextUtil.normaliseLineSeparators
import static org.junit.Assert.assertThat

// Developer is able to easily determine the file(s) that triggered a rebuild
@Ignore
class ContinuousBuildChangeReportingIntegrationTest extends Java7RequiringContinuousIntegrationTest {
    def setup() {
        buildFile << """
            task a {
              inputs.dir "a"
              doLast {}
            }
            task b {
              dependsOn "a"
              inputs.dir "b"
              doLast {}
            }
            task c {
              dependsOn "b"
              inputs.dir "c"
              doLast {}
            }
            task d {
              dependsOn "c"
              inputs.dir "d"
              doLast {}
            }

        """
    }

    def "should report the absolute file path of the file added when 1 file is added to the input directory of each task "(changingInput) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        succeeds("d")
        file("$changingInput/input.txt").text = 'New input file'

        then:
        def result = succeeds()
        sendEOT()
        result.assertOutputContains('new file: ' + file("$changingInput/input.txt").absolutePath + '\nChange detected, executing build...')

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    def "should report the absolute file path of the file added when 3 files are added to the input directory of each task "(changingInput) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        succeeds("d")
        (1..SHOW_INDIVIDUAL_CHANGES_LIMIT).each { file("$changingInput/input${it}.txt").text = 'New input file' }

        then:
        def result = succeeds()
        sendEOT()
        result.assertOutputContains((1..SHOW_INDIVIDUAL_CHANGES_LIMIT).collect { 'new file: ' + file("$changingInput/input${it}.txt").absolutePath }.join('\n') + '\nChange detected, executing build...')

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    def "should report the absolute file path of the first SHOW_INDIVIDUAL_CHANGES_LIMIT changes and report the number of other changes when more that 3 files are added to the input directory of each task"(changingInput) {
        given:
        ['a', 'b', 'c', 'd'].each { file(it).createDir() }

        when:
        succeeds("d")
        (1..9).each { file("$changingInput/input${it}.txt").text = 'New input file' }

        then:
        def result = succeeds()
        sendEOT()
        result.assertOutputContains((1..SHOW_INDIVIDUAL_CHANGES_LIMIT).collect { 'new file: ' + file("$changingInput/input${it}.txt").absolutePath }.join('\n') + '\nand 6 more changes\nChange detected, executing build...')

        where:
        changingInput << ['a', 'b', 'c', 'd']
    }

    private void assertOutputContainsRegexp(ExecutionResult result, String... patternParts) {
        assertOutputContainsRegexp(result, Pattern.compile(normaliseLineSeparators(patternParts.join(''))))
    }

    private void assertOutputContainsRegexp(ExecutionResult result, Pattern pattern) {
        assertThat("Output doesn't match pattern", result.getOutput(), matchesRegexpFind(pattern));
    }

    private static Matcher matchesRegexpFind(final Pattern pattern) {
        return new BaseMatcher() {
            public boolean matches(Object o) {
                return pattern.matcher((CharSequence) o).find();
            }

            public void describeTo(Description description) {
                description.appendText("a CharSequence that contains regexp ").appendValue(pattern);
            }
        };
    }

    def "should report the changes when files are removed"(changingInput, changesCount) {
        given:
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
            (1..11).each { dir.file("input${String.format('%02d',it)}.txt").text = "Input file" }
        }

        when:
        succeeds("d")
        (1..changesCount).each { file("$changingInput/input${String.format('%02d', it)}.txt").delete() }

        then:
        def result = succeeds()
        sendEOT()
        assertOutputContainsRegexp(result, Pattern.quote((1..(Math.min(SHOW_INDIVIDUAL_CHANGES_LIMIT, changesCount))).collect { 'deleted: ' + file("$changingInput/input${String.format('%02d', it)}.txt").absolutePath }.join('\n')),
            (changesCount > SHOW_INDIVIDUAL_CHANGES_LIMIT ? "\nand (${(changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT) * 2}|${changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT}|${changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT + 1}) more changes" : ''),
            '\nChange detected, executing build', Pattern.quote('...'))

        where:
        [changingInput, changesCount] << [['a', 'b', 'c', 'd'], [1, SHOW_INDIVIDUAL_CHANGES_LIMIT, 11]].combinations()
    }

    def "should report the changes when files are modified"(changingInput, changesCount) {
        given:
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
            (1..11).each { dir.file("input${String.format('%02d',it)}.txt").text = "Input file" }
        }

        when:
        succeeds("d")
        (1..changesCount).each { file("$changingInput/input${String.format('%02d', it)}.txt").text = 'File modified' }

        then:
        def result = succeeds()
        sendEOT()
        assertOutputContainsRegexp(result, Pattern.quote((1..(Math.min(SHOW_INDIVIDUAL_CHANGES_LIMIT, changesCount))).collect { 'modified: ' + file("$changingInput/input${String.format('%02d', it)}.txt").absolutePath }.join('\n')),
            (changesCount > SHOW_INDIVIDUAL_CHANGES_LIMIT ? "\nand (${(changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT) * 2}|${changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT}|${changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT + 1}) more changes" : ''),
            '\nChange detected, executing build', Pattern.quote('...'))

        where:
        [changingInput, changesCount] << [['a', 'b', 'c', 'd'], [1, SHOW_INDIVIDUAL_CHANGES_LIMIT, 11]].combinations()
    }

    def "should report the changes when directories are added"(changingInput, changesCount) {
        given:
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
        }

        when:
        succeeds("d")
        (1..changesCount).each { file("$changingInput/input${String.format('%02d', it)}Directory").mkdir() }

        then:
        def result = succeeds()
        sendEOT()
        assertOutputContainsRegexp(result, Pattern.quote((1..(Math.min(SHOW_INDIVIDUAL_CHANGES_LIMIT, changesCount))).collect { 'new directory: ' + file("$changingInput/input${String.format('%02d', it)}Directory").absolutePath }.join('\n')),
            (changesCount > SHOW_INDIVIDUAL_CHANGES_LIMIT ? "\nand (${(changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT) * 2}|${changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT}) more changes" : ''),
            '\nChange detected, executing build', Pattern.quote('...'))

        where:
        [changingInput, changesCount] << [['a', 'b', 'c', 'd'], [1, SHOW_INDIVIDUAL_CHANGES_LIMIT, 11]].combinations()
    }


    def "should report the changes when directories are deleted"(changingInput, changesCount) {
        given:
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
            (1..11).each { dir.file("input${String.format('%02d',it)}Directory").createDir() }
        }

        when:
        succeeds("d")
        (1..changesCount).each { file("$changingInput/input${String.format('%02d', it)}Directory").delete() }

        then:
        def result = succeeds()
        sendEOT()
        assertOutputContainsRegexp(result, Pattern.quote((1..(Math.min(SHOW_INDIVIDUAL_CHANGES_LIMIT, changesCount))).collect { 'deleted: ' + file("$changingInput/input${String.format('%02d', it)}Directory").absolutePath }.join('\n')),
            (changesCount > SHOW_INDIVIDUAL_CHANGES_LIMIT ? "\nand ${(changesCount - SHOW_INDIVIDUAL_CHANGES_LIMIT) * 2} more changes" : ''),
            '\nChange detected, executing build', Pattern.quote('...'))

        where:
        [changingInput, changesCount] << [['a', 'b', 'c', 'd'], [1, SHOW_INDIVIDUAL_CHANGES_LIMIT, 11]].combinations()
    }


    def "should report the changes when multiple changes are made at once"() {
        given:
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
            (1..11).each { dir.file("input${String.format('%02d',it)}.txt").text = 'Input file' }
        }

        when:
        succeeds("d")
        ['a', 'b', 'c', 'd'].each {
            def dir = file(it).createDir()
            dir.file("input12.txt").text = 'New Input file'
            (1..11).each {
                def file = dir.file("input${String.format('%02d', it)}.txt")
                if (it % 2 == 0) {
                    file.text = 'Modified file'
                } else if(it == 1 || it == 7) {
                    file.delete()
                }
            }
            dir.file("input13.txt").text = 'New Input file'
        }

        then:
        def result = succeeds()
        sendEOT()
        assertOutputContainsRegexp(result, '\nand ', '(33|40)', ' more changes\nChange detected, executing build', Pattern.quote('...'))
    }
}
