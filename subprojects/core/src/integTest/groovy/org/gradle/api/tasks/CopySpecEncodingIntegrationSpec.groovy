/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths

@DoesNotSupportNonAsciiPaths(reason = "Uses non-Unicode default charset")
class CopySpecEncodingIntegrationSpec extends AbstractIntegrationSpec {

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
}
