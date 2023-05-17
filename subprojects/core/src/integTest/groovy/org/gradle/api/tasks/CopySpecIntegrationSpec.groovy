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

package org.gradle.api.tasks

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Issue

class CopySpecIntegrationSpec extends AbstractIntegrationSpec implements UnreadableCopyDestinationFixture {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    def "copy task uses platform charset to filter text files by default"() {
        given:
        file('files').createDir()
        file('files/accents.c').write('éàüî $one', 'ISO-8859-1')
        buildScript """
            task (copy, type: Copy) {
                from 'files'
                into 'dest'
                expand(one: 1)
            }
        """.stripIndent()
        executer.beforeExecute { it.withDefaultCharacterEncoding('ISO-8859-1') }

        when:
        run 'copy'

        then:
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'

        when:
        file('files/accents.c').write('áëü $one', 'ISO-8859-1')
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest/accents.c').getText('ISO-8859-1') == 'áëü 1'
    }

    def "copy task uses declared charset to filter text files"() {
        given:
        file('files').createDir()
        file('files/accents.c').write('éàüî $one', 'ISO-8859-1')
        buildScript """
            task (copy, type: Copy) {
                from 'files'
                into 'dest'
                expand(one: 1)
                filteringCharset = 'ISO-8859-1'
            }
        """.stripIndent()
        executer.beforeExecute { it.withDefaultCharacterEncoding('UTF-8') }

        when:
        run 'copy'

        then:
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'

        when:
        file('files/accents.c').write('áëü $one', 'ISO-8859-1')
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest/accents.c').getText('ISO-8859-1') == 'áëü 1'
    }

    def "copy action uses platform charset to filter text files by default"() {
        given:
        file('files').createDir()
        file('files/accents.c').write('éàüî $one', 'ISO-8859-1')
        buildScript """
            task copy {
                def fs = services.get(FileSystemOperations)
                doLast {
                    fs.copy {
                        from 'files'
                        into 'dest'
                        expand(one: 1)
                    }
                }
            }
        """.stripIndent()
        executer.beforeExecute { it.withDefaultCharacterEncoding('ISO-8859-1') }

        when:
        run 'copy'

        then:
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'
    }

    def "copy action uses declared charset to filter text files"() {
        given:
        file('files').createDir()
        file('files/accents.c').write('éàüî $one', 'ISO-8859-1')
        buildScript """
            task copy {
                def fs = services.get(FileSystemOperations)
                doLast {
                    fs.copy {
                        from 'files'
                        into 'dest'
                        expand(one: 1)
                        filteringCharset = 'ISO-8859-1'
                    }
                }
            }
        """.stripIndent()
        executer.beforeExecute { it.withDefaultCharacterEncoding('UTF-8') }

        when:
        run 'copy'

        then:
        file('dest/accents.c').getText('ISO-8859-1') == 'éàüî 1'
    }

    def "can use filesMatching with List"() {
        given:
        buildScript """
            task (copy, type: Copy) {
                from 'src'
                into 'dest'
                filesMatching(['**/ignore/**', '**/sub/**']) {
                    name = "matched\${name}"
                }
            }
        """.stripIndent()

        when:
        succeeds 'copy'

        then:
        file('dest/one/ignore/matchedbad.file').exists()
        file('dest/two/ignore/matchedbad.file').exists()
        !file('dest/one/matchedone.a').exists()
    }

    def "can use filesNotMatching with List"() {
        given:
        buildScript """
            task (copy, type: Copy) {
                from 'src'
                into 'dest'
                filesNotMatching(['**/ignore/**', '**/sub/**']) {
                    name = "matched\${name}"
                }
            }
        """.stripIndent()

        when:
        succeeds 'copy'

        then:
        !file('dest/one/ignore/matchedbad.file').exists()
        !file('dest/two/ignore/matchedbad.file').exists()
        file('dest/one/matchedone.a').exists()
    }

    @NotYetImplemented
    @Issue("gradle/gradle#789")
    def "can copy files with supplementary characters or surrogate pairs in file names"() {
        given:
        buildScript """
            task(copy, type: Copy) {
                from 'src'
                into 'dest'
            }
        """.stripIndent()

        and:
        file('src/アンドリューは本当に凄いですawesomeだと思います.txt') << 'some content'
        file('src/𩸽.txt') << 'some content'
        file('src/😀.txt') << 'some content'

        when:
        succeeds 'copy'

        then:
        file('dest/アンドリューは本当に凄いですawesomeだと思います.txt').exists()
        file('dest/𩸽.txt').exists()
        file('dest/😀.txt').exists()
        false // TODO This test can pass on Windows with proper locale, this force the test to fail, remove once fixed
    }

    @Requires(UnitTestPreconditions.UnixDerivative)
    @Issue("https://github.com/gradle/gradle/issues/2552")
    def "copying files to a directory with named pipes fails"() {
        def input = file("input.txt").createFile()

        def outputDirectory = file("output").createDir()
        def pipe = outputDirectory.file("testPipe").createNamedPipe()

        buildFile << """
            task copy(type: Copy) {
                from '${input.name}'
                into '${outputDirectory.name}'
            }
        """

        when:
        runAndFail "copy"
        then:
        expectUnreadableCopyDestinationFailure()
        failureHasCause("java.io.IOException: Cannot snapshot ${pipe}: not a regular file")

        cleanup:
        pipe.delete()
    }
}
