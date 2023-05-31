/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.plugins.ExtensionAware
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.util.Matchers
import org.gradle.util.internal.ToBeImplemented
import org.junit.Rule
import spock.lang.Issue

class CopyTaskIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    private final static DocumentationRegistry DOCUMENTATION_REGISTRY = new DocumentationRegistry()

    def "copies everything by default"() {
        given:
        file("files/sub/a.txt").createFile()
        file("files/sub/dir/b.txt").createFile()
        file("files/c.txt").createFile()
        file("files/sub/empty").createDir()
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'c.txt',
            'sub/empty'
        )

        when:
        run 'copy'

        then:
        skipped(":copy")
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'c.txt',
            'sub/empty'
        )

        when:
        file("files/sub/d.txt").createFile()
        run 'copy'

        then:
        executedAndNotSkipped(":copy")
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'sub/d.txt',
            'c.txt',
            'sub/empty'
        )
    }

    def "is out-of-date when adding an empty directory"() {
        given:
        file("files/sub/a.txt").createFile()
        file("files/sub/dir/b.txt").createFile()
        file("files/c.txt").createFile()
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
            }
        '''

        when:
        run 'copy'
        then:
        executedAndNotSkipped(":copy")

        when:
        file("files/sub/empty").createDir()
        run 'copy'
        then:
        executedAndNotSkipped(":copy")

        when:
        run 'copy'
        then:
        skipped(":copy")
    }

    def "single source with include and exclude pattern"() {
        given:
        file("files/sub/a.txt").createFile()
        file("files/sub/dir/b.txt").createFile()
        file("files/sub/ignore/ignore.txt").createFile()
        file("files/dir/sub/dir/c.txt").createFile()
        file("files/dir/sub/dir/ignore/dir/ignore.txt").createFile()
        file("files/ignore/sub/ignore.txt").createFile()
        file("files/ignore.txt").createFile()
        file("files/other/ignore.txt").createFile()
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               include '**/sub/**'
               exclude '**/ignore/**'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'dir/sub/dir/c.txt',
            'other'
        )

        when:
        file("files/sub/ignore/ignore-2.txt").createFile()
        run 'copy'

        then:
        skipped(":copy")
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'dir/sub/dir/c.txt',
            'other'
        )

        when:
        file("files/sub/d.txt").createFile()
        run 'copy'

        then:
        executedAndNotSkipped(":copy")
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/d.txt',
            'sub/dir/b.txt',
            'dir/sub/dir/c.txt',
            'other'
        )
    }

    def "single source with include and exclude Groovy closures"() {
        given:
        file('files/a.a').createFile()
        file('files/a.b').createFile()
        file('files/dir/a.a').createFile()
        file('files/dir/a.b').createFile()
        file('files/dir/ignore.c').createFile()
        file('files/dir.b/a.a').createFile()
        file('files/dir.b/a.b').createFile()
        buildScript '''
            task (copy, type:Copy) {
               from 'files'
               into 'dest'
               include { fte -> !fte.file.name.endsWith('b') }
               exclude { fte -> fte.file.name.contains('ignore') }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'a.a',
            'dir/a.a'
        )

        when:
        file('files/dir/ignore.d').createFile()
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants(
            'a.a',
            'dir/a.a'
        )

        when:
        file('files/dir/a.c').createFile()
        file('files/dir/ignore.e').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'a.a',
            'dir/a.a',
            'dir/a.c'
        )
    }

    def "can expand tokens when copying"() {
        file('files/a.txt').text = "\$one,\${two}"
        buildScript """
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                expand(one: '1', two: 2)
            }
        """

        when:
        run 'copy'

        then:
        file('dest/a.txt').text == "1,2"

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest/a.txt').text == "1,2"

        when:
        file('files/a.txt').text = "\${one} + \${two}"
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest/a.txt').text == "1 + 2"
    }

    def "can expand tokens with escaped backslash when copying"() {
        file('files/a.txt').text = "\$one\\n\${two}"
        buildScript """
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                expand(one: '1', two: 2) {
                    escapeBackslash = true
                }
            }
        """

        when:
        run 'copy'

        then:
        file('dest/a.txt').text == "1\\n2"
    }

    def "can expand tokens but not escape backslash by default when copying"() {
        file('files/a.txt').text = "\$one\\n\${two}"
        buildScript """
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                expand(one: '1', two: 2)
            }
        """

        when:
        run 'copy'

        then:
        file('dest/a.txt').text == "1\n2"
    }

    def "can filter content using a filtering Reader when copying"() {
        file('files/a.txt').text = "one"
        file('files/b.txt').text = "two"
        buildScript """
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                filter(SubstitutingFilter)
            }

            class SubstitutingFilter extends FilterReader {
                SubstitutingFilter(Reader reader) {
                    super(new StringReader(reader.text.replaceAll("one", "1").replaceAll("two", "2")))
                }
            }
        """

        when:
        run 'copy'

        then:
        file('dest/a.txt').text == "1"
        file('dest/b.txt').text == "2"

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest/a.txt').text == "1"

        when:
        file('files/a.txt').text = "one + two"
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest/a.txt').text == "1 + 2"
    }

    def "can filter content using a Groovy closure when copying"() {
        file('files/a.txt').text = "one"
        file('files/b.txt').text = "two"
        buildScript """
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                filter { return it.replaceAll("one", "1").replaceAll("two", "2") }
            }
        """

        when:
        run 'copy'

        then:
        file('dest/a.txt').text == "1"
        file('dest/b.txt').text == "2"

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest/a.txt').text == "1"

        when:
        file('files/a.txt').text = "one + two"
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest/a.txt').text == "1 + 2"
    }

    def "useful help message when property cannot be expanded"() {
        given:
        buildFile << """
            task copy (type: Copy) {
                // two.a expects "one" to be defined
                from('src/two/two.a')
                into('dest')
                expand("notused": "notused")
            }
        """
        when:
        fails 'copy'
        then:
        failure.assertHasCause("Could not copy file '${file("src/two/two.a")}' to '${file("dest/two.a")}'.")
        failure.assertHasCause("Missing property (one) for Groovy template expansion. Defined keys [notused].")
    }

    def "useful help message when property cannot be expanded in filter chain"() {
        given:
        buildFile << """
            task copy (type: Copy) {
                // two.a expects "one" to be defined
                from('src/two/two.a')
                into('dest')
                // expect "two" to be defined as well
                filter { line -> '\$two ' + line }
                expand("notused": "notused")
            }
        """
        when:
        fails 'copy'
        then:
        failure.assertHasCause("Could not copy file '${file("src/two/two.a")}' to '${file("dest/two.a")}'.")
        failure.assertHasCause("Missing property (two) for Groovy template expansion. Defined keys [notused].")
    }

    def "multiple source with inherited include and exclude patterns"() {
        given:
        file('files/one/one.a').createFile()
        file('files/one/one.ignore').createFile()
        file('files/one/sub/one.a').createFile()
        file('files/one/sub/ignore/ignore.a').createFile()
        file('files/two/two.b').createFile()
        file('files/two/two.ignore').createFile()
        file('files/two/sub/two.b').createFile()
        file('files/two/sub/ignore/ignore.b').createFile()
        buildScript '''
            task (copy, type:Copy) {
               into 'dest'
               from('files/one') {
                  into '1'
                  include '**/*.a'
               }
               from('files/two') {
                  into '2'
                  include '**/*.b'
               }
               exclude '**/ignore/**'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            '1/one.a',
            '1/sub/one.a',
            '2/two.b',
            '2/sub/two.b'
        )

        when:
        file('files/two/ignore-more.ignore').createFile() // not an input
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants(
            '1/one.a',
            '1/sub/one.a',
            '2/two.b',
            '2/sub/two.b'
        )

        when:
        file('files/one/more.a').createFile()
        file('files/two/more.b').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            '1/one.a',
            '1/more.a',
            '1/sub/one.a',
            '2/two.b',
            '2/more.b',
            '2/sub/two.b'
        )
    }

    def "multiple sources with inherited destination"() {
        given:
        file('files/one/one.a').createFile()
        file('files/one/one.ignore').createFile()
        file('files/one/sub/ignore.a').createFile()
        file('files/two/two.b').createFile()
        file('files/two/two.ignore').createFile()
        file('files/two/sub/two.b').createFile()
        file('files/two/sub/ignore.a').createFile()
        buildScript '''
            task (copy, type:Copy) {
               into 'dest'
               into('common') {
                  from('files/one') {
                     into 'a/one'
                     include '*.a'
                  }
                  into('b') {
                     from('files/two') {
                        into 'two'
                        include '**/*.b'
                     }
                  }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'common/a/one/one.a',
            'common/b/two/two.b',
            'common/b/two/sub/two.b'
        )

        when:
        file('files/one/dir/ignore.a').createFile()
        file('files/two/sub/ignore.a').createFile()
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants(
            'common/a/one/one.a',
            'common/b/two/two.b',
            'common/b/two/sub/two.b'
        )

        when:
        file('files/one/more.a').createFile()
        file('files/two/sub/more.b').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'common/a/one/one.a',
            'common/a/one/more.a',
            'common/b/two/two.b',
            'common/b/two/sub/two.b',
            'common/b/two/sub/more.b'
        )
    }

    def "can rename files using a regexp string and replacement pattern"() {
        given:
        file('files/one.a').createFile()
        file('files/one.b').createFile()
        file('files/dir/two.a').createFile()
        file('files/dir/two.b').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                rename '(.*).a', '\$1.renamed'
            }
        '''

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'dir/two.renamed',
            'dir/two.b'
        )

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'dir/two.renamed',
            'dir/two.b'
        )

        when:
        file('files/one.c').createNewFile()
        file('files/dir/another.a').createNewFile()

        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'one.c',
            'dir/two.renamed',
            'dir/two.b',
            'dir/another.renamed'
        )
    }

    def "can rename files using a Groovy closure"() {
        given:
        file('files/one.a').createFile()
        file('files/one.b').createFile()
        file('files/dir/two.a').createFile()
        file('files/dir/two.b').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                rename {
                    println("rename $it")
                    if (it.endsWith('.b')) {
                        return null
                    } else {
                        return it.replace('.a', '.renamed')
                    }
                }
            }
        '''

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'dir/two.renamed',
            'dir/two.b'
        )

        when:
        run 'copy'

        then:
        skipped(':copy')
        output.count("rename") == 0
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'dir/two.renamed',
            'dir/two.b'
        )

        when:
        file('files/one.c').createNewFile()
        file('files/dir/another.a').createNewFile()

        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        output.count("rename") == 6
        outputContains("rename one.a")
        outputContains("rename another.a")
        file('dest').assertHasDescendants(
            'one.renamed',
            'one.b',
            'one.c',
            'dir/two.renamed',
            'dir/two.b',
            'dir/another.renamed'
        )
    }

    def "rename"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               exclude '**/ignore/**'
               rename '(.*).a', '\$1.renamed'
               rename { it.startsWith('one.') ? "renamed_$it" : it }
               rename { it.endsWith('two.b') ? null : it}
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'root.renamed',
            'root.b',
            'accents.c',
            'one/renamed_one.renamed',
            'one/renamed_one.b',
            'one/sub/onesub.renamed',
            'one/sub/onesub.b',
            'two/two.renamed',
            'two/two.b' //do not rename with 'rename { null }'
        )
    }

    def "copy action"() {
        given:
        buildScript '''
            task copyIt {
                doLast {
                    copy {
                        from 'src'
                        into 'dest'
                        exclude '**/ignore/**'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'root.a',
            'root.b',
            'accents.c',
            'one/one.a',
            'one/one.b',
            'one/sub/onesub.a',
            'one/sub/onesub.b',
            'two/two.a',
            'two/two.b',
        )
    }

    def "copy single files"() {
        given:
        buildScript '''
            task copyIt {
                doLast {
                    copy {
                        from 'src/one/one.a', 'src/two/two.a'
                        into 'dest/two'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'copyIt'

        then:
        file('dest').assertHasDescendants(
            'two/one.a',
            'two/two.a',
        )
    }

    /*
     * two.a starts off with "$one\n${one+1}\n${one+1+1}\n"
     * If these filters are chained in the correct order, you should get 6, 11, and 16
     */

    def "copy multiple filter test"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               into 'dest\'
               expand(one: 1)
               filter { (Integer.parseInt(it) * 10) as String }
               filter { (Integer.parseInt(it) + 2) as String }
               from('src/two/two.a') {
                 filter { (Integer.parseInt(it) / 2) as String }
               }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        def it = file('dest/two.a').readLines().iterator()
        it.next().startsWith('6')
        it.next().startsWith('11')
        it.next().startsWith('16')
    }

    def "can rename files in eachFile() action defined using Groovy closure"() {
        given:
        file('files/a.txt').createFile()
        file('files/dir/b.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                into 'dest'
                from 'files'
                eachFile { fcd ->
                    println("visiting ${fcd.path}")
                    fcd.path = "sub/${fcd.path}"
                }
            }
        '''

        when:
        run 'copy'

        then:
        output.count('visiting ') == 2
        outputContains('visiting a.txt')
        outputContains('visiting dir/b.txt')
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'dir' // directories are not passed to eachFile
        )

        when:
        run 'copy'

        then:
        skipped(':copy')
        output.count('visiting ') == 0
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/dir/b.txt',
            'dir'
        )

        when:
        file('files/c.txt').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        output.count('visiting ') == 3
        outputContains('visiting a.txt')
        outputContains('visiting dir/b.txt')
        outputContains('visiting c.txt')
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'sub/a.txt',
            'sub/c.txt',
            'sub/dir/b.txt',
            'dir'
        )
    }

    def "can rename files that match a pattern in filesMatching() action defined using Groovy closure"() {
        given:
        file('files/a.txt').createFile()
        file('files/dir/b.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                into 'dest'
                from 'files'
                filesMatching('dir/**') { fcd ->
                    println("visiting ${fcd.path}")
                    fcd.path = "sub/${fcd.path}"
                }
            }
        '''

        when:
        run 'copy'

        then:
        output.count('visiting ') == 1
        outputContains('visiting dir/b.txt')
        file('dest').assertHasDescendants(
            'a.txt',
            'sub/dir/b.txt',
            'dir' // directories are not passed to filesMatching
        )

        when:
        run 'copy'

        then:
        skipped(':copy')
        output.count('visiting ') == 0
        file('dest').assertHasDescendants(
            'a.txt',
            'sub/dir/b.txt',
            'dir'
        )

        when:
        file('files/c.txt').createFile()
        file('files/dir/d/e.txt').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        output.count('visiting ') == 2
        outputContains('visiting dir/b.txt')
        outputContains('visiting dir/d/e.txt')
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'a.txt',
            'c.txt',
            'sub/dir/b.txt',
            'sub/dir/d/e.txt',
            'dir/d'
        )
    }

    def "can rename files that do not match a pattern in filesNotMatching() action defined using Groovy closure"() {
        given:
        file('files/a.txt').createFile()
        file('files/dir/b.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                into 'dest'
                from 'files'
                filesNotMatching('*.txt') { fcd ->
                    println("visiting ${fcd.path}")
                    fcd.path = "sub/${fcd.path}"
                }
            }
        '''

        when:
        run 'copy'

        then:
        output.count('visiting ') == 1
        outputContains('visiting dir/b.txt')
        file('dest').assertHasDescendants(
            'a.txt',
            'sub/dir/b.txt',
            'dir' // directories are not passed to filesNotMatching
        )

        when:
        run 'copy'

        then:
        skipped(':copy')
        output.count('visiting ') == 0
        file('dest').assertHasDescendants(
            'a.txt',
            'sub/dir/b.txt',
            'dir'
        )

        when:
        file('files/c.txt').createFile()
        file('files/dir/d/e.txt').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        output.count('visiting ') == 2
        outputContains('visiting dir/b.txt')
        outputContains('visiting dir/d/e.txt')
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'a.txt',
            'c.txt',
            'sub/dir/b.txt',
            'sub/dir/d/e.txt',
            'dir/d'
        )
    }

    def "chained transformations"() {
        given:
        buildScript '''
            task copy(type: Copy) {
                into 'dest\'
                rename '(.*).a', '\$1.renamed'
                eachFile { fcd -> if (fcd.path.contains('/ignore/')) { fcd.exclude() } }
                eachFile { fcd -> if (fcd.relativePath.segments.length > 1) { fcd.relativePath = fcd.relativePath.prepend('prefix') }}
                filter(org.apache.tools.ant.filters.PrefixLines, prefix: 'line: ')
                eachFile { fcd -> fcd.filter { it.replaceAll('^line:', 'prefix:') } }
                from ('src') {
                    rename '(.*).renamed', '\$1.renamed_twice'
                    eachFile { fcd -> fcd.path = fcd.path.replaceAll('/one/sub/', '/one_sub/') }
                    eachFile { fcd -> if (fcd.path.contains('/two/')) { fcd.exclude() } }
                    eachFile { fcd -> fcd.filter { "[$it]" } }
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'root.renamed_twice',
            'root.b',
            'accents.c',
            'prefix/one/one.renamed_twice',
            'prefix/one/one.b',
            'prefix/one_sub/onesub.renamed_twice',
            'prefix/one_sub/onesub.b',
            'one/ignore',
            'one/sub/ignore',
            'two/ignore'
        )
        def it = file('dest/root.renamed_twice').readLines().iterator()
        it.next().equals('[prefix: line 1]')
        it.next().equals('[prefix: line 2]')
    }

    def "copy from location specified lazily using Groovy closure"() {
        given:
        file('files/a.txt').createFile()
        file('files/dir/b.txt').createFile()

        buildScript '''
            def location = null

            task copy(type: Copy) {
                into 'dest'
                from { file(location) }
            }

            location = 'files'
        '''

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'a.txt',
            'dir/b.txt'
        )

        when:
        run 'copy'

        then:
        skipped(':copy')

        when:
        file('files/c.txt').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants(
            'a.txt',
            'dir/b.txt',
            'c.txt'
        )
    }

    def "copy from file tree"() {
        given:
        buildScript '''
        task cpy {
            doLast {
                copy {
                    from fileTree(dir: 'src', excludes: ['**/ignore/**'], includes: ['*', '*/*'])
                    into 'dest\'
                }
            }
        }
        '''.stripIndent()

        when:
        run 'cpy'

        then:
        file('dest').assertHasDescendants(
            'root.a',
            'root.b',
            'accents.c',
            'one/one.a',
            'one/one.b',
            'two/two.a',
            'two/two.b',
            'one/sub'
        )
    }

    def "copy from file collection"() {
        given:
        buildScript '''
            task copy {
                doLast {
                    copy {
                        from files('src')
                        into 'dest\'
                        exclude '**/ignore/**\'
                        exclude '*/*/*/**\'
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'root.a',
            'root.b',
            'accents.c',
            'one/one.a',
            'one/one.b',
            'two/two.a',
            'two/two.b',
            'one/sub'
        )
    }

    def "copy from composite file collection"() {
        given:
        file('a.jar').touch()
        buildScript '''
            configurations { compile }
            dependencies { compile files('a.jar') }
            task copy {
                doLast {
                    copy {
                        from files('src2') + fileTree('src') { exclude '**/ignore/**' } + configurations.compile
                        into 'dest'
                        include { fte -> fte.relativePath.segments.length < 3 && (fte.file.directory || fte.file.name.contains('a')) }
                    }
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'root.a',
            'accents.c',
            'one/one.a',
            'two/two.a',
            'three/three.a',
            'a.jar',
            'one/sub'
        )
    }

    def "copy from task"() {
        given:
        buildScript '''
            configurations { compile }
            dependencies { compile files('a.jar') }
            task fileProducer {
                outputs.file 'build/out.txt'
                doLast {
                    file('build/out.txt').text = 'some content'
                }
            }
            task dirProducer {
                outputs.dir 'build/outdir'
                doLast {
                    file('build/outdir').mkdirs()
                    file('build/outdir/file1.txt').text = 'some content'
                    file('build/outdir/sub').mkdirs()
                    file('build/outdir/sub/file2.txt').text = 'some content'
                }
            }
            task copy(type: Copy) {
                from fileProducer, dirProducer
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'out.txt',
            'file1.txt',
            'sub/file2.txt'
        )
    }

    def "copy from task outputs"() {
        given:
        buildScript '''
            configurations { compile }
            dependencies { compile files('a.jar') }
            task fileProducer {
                outputs.file 'build/out.txt'
                doLast {
                    file('build/out.txt').text = 'some content'
                }
            }
            task dirProducer {
                outputs.dir 'build/outdir'
                doLast {
                    file('build/outdir').mkdirs()
                    file('build/outdir/file1.txt').text = 'some content'
                    file('build/outdir/sub').mkdirs()
                    file('build/outdir/sub/file2.txt').text = 'some content'
                }
            }
            task copy(type: Copy) {
                from fileProducer.outputs, dirProducer.outputs
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'out.txt',
            'file1.txt',
            'sub/file2.txt'
        )
    }

    def "copy from task provider"() {
        given:
        buildScript '''
            configurations { compile }
            dependencies { compile files('a.jar') }
            def fileProducer = tasks.register("fileProducer") {
                outputs.file 'build/out.txt'
                doLast {
                    file('build/out.txt').text = 'some content'
                }
            }
            def dirProducer = tasks.register("dirProducer") {
                outputs.dir 'build/outdir'
                doLast {
                    file('build/outdir').mkdirs()
                    file('build/outdir/file1.txt').text = 'some content'
                    file('build/outdir/sub').mkdirs()
                    file('build/outdir/sub/file2.txt').text = 'some content'
                }
            }
            task copy(type: Copy) {
                from fileProducer, dirProducer
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'copy', '-i'

        then:
        file('dest').assertHasDescendants(
            'out.txt',
            'file1.txt',
            'sub/file2.txt'
        )
    }

    def "copy with CopySpec"() {
        given:
        buildScript '''
            def parentSpec = copySpec {
                from 'src'
                exclude '**/ignore/**'
                include '*/*.a'
                into 'subdir'
            }
            task copy(type: Copy) {
                into 'dest'
                with parentSpec
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'subdir/one/one.a',
            'subdir/two/two.a'
        )
    }

    def "transform with CopySpec"() {
        given:
        buildScript '''
            def parentSpec = copySpec {
                from 'src'
                include '*/*.a'
                into 'subdir'
                eachFile { fcd -> fcd.relativePath = fcd.relativePath.prepend('transformedAgain')}
            }
            task copy(type: Copy) {
                into 'dest'
                with parentSpec
                eachFile { fcd -> fcd.relativePath = fcd.relativePath.prepend('transformed') }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'transformedAgain/transformed/subdir/one/one.a',
            'transformedAgain/transformed/subdir/two/two.a',
            'subdir/one',
            'subdir/two'
        )
    }

    def "include exclude with CopySpec"() {
        given:
        buildScript '''
            def parentSpec = copySpec {
                from 'src'
                include '**/one/**'
                exclude '**/ignore/**'
                into 'subdir'
            }
            task copy(type: Copy) {
                into 'dest'
                include '**/two/**'
                exclude '**/*.b'
                with parentSpec
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'subdir/one/one.a',
            'subdir/one/sub/onesub.a',
            'subdir/two/two.a'
        )
    }

    /*
     * two.a starts off with "$one\n${one+1}\n${one+1+1}\n"
     * If these filters are chained in the correct order, you should get 6, 11, and 16
     */

    def "multiple filter with CopySpec"() {
        given:
        buildScript '''
            def parentSpec = copySpec {
                from('src/two/two.a')
                filter { (Integer.parseInt(it) / 2) as String }
              }
              task (copy, type:Copy) {
               into 'dest'
               expand(one: 1)
               filter { (Integer.parseInt(it) * 10) as String }
               filter { (Integer.parseInt(it) + 2) as String }
               with parentSpec
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        def it = file('dest/two.a').readLines().iterator()
        it.next().startsWith('6')
        it.next().startsWith('11')
        it.next().startsWith('16')
    }

    def "rename with CopySpec"() {
        given:
        buildScript '''
            def parentSpec = copySpec {
               from 'src/one'
               exclude '**/ignore/**'
               rename '(.*).b$', '$1.renamed'
            }
            task (copy, type:Copy) {
               with parentSpec
               into 'dest'
               rename { it.startsWith('one.') ? "renamed_$it" : it }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants(
            'renamed_one.renamed',
            'renamed_one.a',
            'sub/onesub.renamed',
            'sub/onesub.a',
        )
    }

    def 'include and exclude patterns are case sensitive by default'() {
        given:
        file('files/sub/a.TXT').createFile()
        file('files/sub/b.txt').createFile()
        file('files/sub/c.Txt').createFile()
        file('files/EXCLUDE/a.TXT').createFile()
        file('files/sub/Exclude/a.TXT').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                include '**/*.TXT'
                exclude '**/EXCLUDE/**'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('sub/a.TXT', 'sub/Exclude/a.TXT')

        when:
        run 'copy'

        then:
        file('files/d.txt').createFile()
        skipped(':copy')

        when:
        file('files/a.TXT').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('sub/a.TXT', 'sub/Exclude/a.TXT', 'a.TXT')
    }

    def 'include and exclude patterns are case insensitive when enabled'() {
        given:
        file('files/sub/a.TXT').createFile()
        file('files/sub/b.txt').createFile()
        file('files/sub/c.Txt').createFile()
        file('files/EXCLUDE/a.TXT').createFile()
        file('files/sub/Exclude/a.TXT').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                include '**/*.TXT'
                exclude '**/EXCLUDE/**'
                caseSensitive = false
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('sub/a.TXT', 'sub/b.txt', 'sub/c.Txt')

        when:
        run 'copy'

        then:
        file('files/exclude/d.txt').createFile()
        skipped(':copy')
        file('dest').assertHasDescendants('sub/a.TXT', 'sub/b.txt', 'sub/c.Txt')

        when:
        file('files/d.TXT').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('sub/a.TXT', 'sub/b.txt', 'sub/c.Txt', 'd.TXT')
    }

    def "empty directories are copied by default"() {
        given:
        file('files/emptyDir').createDir()
        file('files/yet/another/veryEmptyDir').createDir()
        // need to include a file in the copy, otherwise copy task says "no source files"
        file('files/dummy').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('emptyDir', 'dummy', 'yet/another/veryEmptyDir')
        file('dest/emptyDir').assertIsEmptyDir()
        file('dest/yet/another/veryEmptyDir').assertIsEmptyDir()

        when:
        run 'copy'

        then:
        skipped(':copy')

        when:
        file('files/more').createDir()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('emptyDir', 'dummy', 'more', 'yet/another/veryEmptyDir')
        file('dest/more').assertIsEmptyDir()
    }

    def "empty dirs are not copied if corresponding option is set to false"() {
        given:
        file('files/emptyDir').createDir()
        file('files/yet/another/veryEmptyDir').createDir()
        file('files/one.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'files'
                into 'dest'
                includeEmptyDirs = false
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('one.txt')

        when:
        file('files/more').createDir()
        run 'copy'

        then:
        executedAndNotSkipped(':copy') // TODO - should be skipped
        file('dest').assertHasDescendants('one.txt')

        when:
        file('files/more/more.txt').createFile()
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('one.txt', 'more/more.txt')
    }

    def "copy fails by default when duplicates are present"() {
        given:
        file('dir1/path/file.txt').createFile() << 'f1'
        file('dir2/path/file.txt').createFile() << 'f2'
        buildScript '''
            task copy(type: Copy) {
                from 'dir1'
                from 'dir2'
                into 'dest'
            }
        '''.stripIndent()

        when:
        fails 'copy'

        then:
        failure.assertHasCause "Entry path/file.txt is a duplicate but no duplicate handling strategy has been set. Please refer to ${DOCUMENTATION_REGISTRY.getDslRefForProperty(Copy.class, "duplicatesStrategy")} for details."

        when:
        buildFile << """
            tasks.withType(Copy).configureEach {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        """
        run 'copy'

        then:
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'f1'
    }

    def "copy excludes duplicates when flag is set, stopping at first duplicate"() {
        given:
        file('dir1/path/file.txt').createFile() << 'f1'
        file('dir2/path/file.txt').createFile() << 'f2'
        buildScript '''
            task copy(type: Copy) {
                from 'dir1'
                from 'dir2'
                into 'dest'
                duplicatesStrategy = 'exclude'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'f1'

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'f1'

        when:
        file('dir1/path/file.txt').text = 'new'
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'new'
    }

    def "copy includes duplicates when flag is set, overwriting each duplicate"() {
        given:
        file('dir1/path/file.txt').createFile() << 'f1'
        file('dir2/path/file.txt').createFile() << 'f2'
        buildScript '''
            task copy(type: Copy) {
                from 'dir1'
                from 'dir2'
                into 'dest'
                duplicatesStrategy = 'include'
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'f2'

        when:
        run 'copy'

        then:
        skipped(':copy')
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'f2'

        when:
        file('dir2/path/file.txt').text = 'new'
        run 'copy'

        then:
        executedAndNotSkipped(':copy')
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').text == 'new'
    }

    def "renamed file can be treated as duplicate"() {
        given:
        file('dir1', 'path', 'file.txt').createFile() << 'file1'
        file('dir2', 'path', 'file2.txt').createFile() << 'file2'
        buildScript '''
            task copy(type: Copy) {
                from 'dir1'
                from 'dir2'
                rename 'file2.txt', 'file.txt'
                into 'dest'
                eachFile { it.duplicatesStrategy = 'exclude' }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').assertContents(Matchers.containsText("file1"))
    }

    def "each chained matching rule always matches against initial source path"() {
        given:
        file('path/abc.txt').createFile() << 'test file with $attr'
        file('path/bcd.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    path = path + '.template'
                }
                filesMatching ('**/a*') {
                    expand(attr: 'some value')
                    path = path.replace('template', 'concrete')
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('bcd.txt', 'abc.txt.concrete')
        file('dest/abc.txt.concrete').text == 'test file with some value'
    }

    def "chained matching rules do not match against destination path set by previous chain element"() {
        given:
        file('path/abc.txt').createFile() << 'test file with $attr'
        file('path/bcd.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    path = path + '.template'
                }
                filesMatching ('**/*.template') {
                    expand(attr: 'some value')
                    path = path.replace('template', 'concrete')
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('bcd.txt', 'abc.txt.template')
        file('dest/abc.txt.template').text == 'test file with $attr'
    }

    def "access source name from file copy details"() {
        given:
        file('path/abc.txt').createFile() << 'content'
        file('path/bcd.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    name = "DEST-" + sourceName
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('bcd.txt', 'DEST-abc.txt')
        file('dest/DEST-abc.txt').text == 'content'
    }

    def "access source path from file copy details"() {
        given:
        file('path/abc.txt').createFile() << 'content'
        file('path/bcd.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    path = sourcePath.replace('txt', 'log')
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('bcd.txt', 'abc.log')
        file('dest/abc.log').text == 'content'
    }

    def "access relative source path from file copy details"() {
        given:
        file('path/abc.txt').createFile() << 'content'
        file('path/bcd.txt').createFile()
        buildScript '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    relativePath = relativeSourcePath.replaceLastName('abc.log')
                }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        file('dest').assertHasDescendants('bcd.txt', 'abc.log')
        file('dest/abc.log').text == 'content'
    }

    def "single line removed"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
                from "src/two/two.b"
                into "dest"
                def lineNumber = 1
                filter { lineNumber++ % 2 == 0 ? null : it }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        def it = file('dest/two.b').readLines().iterator()
        it.next().startsWith('one')
        it.next().startsWith('three')
    }

    def "all lines removed"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
                from "src/two/two.b"
                into "dest"
                def lineNumber = 1
                filter { null }
            }
        '''.stripIndent()

        when:
        run 'copy'

        then:
        !file('dest/two.b').readLines().iterator().hasNext()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with non-unicode platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles {
                doLast {
                    copy {
                        from 'res'
                        into 'build/resources'
                    }
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withDefaultCharacterEncoding("ISO-8859-1").withTasks("copyFiles")
        executer.run()

        then:
        file("build/resources", weirdFileName).exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2181")
    def "can copy files with unicode characters in name with default platform encoding"() {
        given:
        def weirdFileName = "القيادة والسيطرة - الإدارة.lnk"

        buildFile << """
            task copyFiles {
                doLast {
                    copy {
                        from 'res'
                        into 'build/resources'
                    }
                }
            }
        """

        file("res", weirdFileName) << "foo"

        when:
        executer.withTasks("copyFiles").run()

        then:
        file("build/resources", weirdFileName).exists()
    }

    def "nested specs and details arent extensible objects"() {
        given:
        file("a/a.txt").touch()

        buildScript """
            task copy(type: Copy) {
                assert delegate instanceof ${ExtensionAware.name}
                into "out"
                from "a", {
                    assert !(delegate instanceof ${ExtensionAware.name})
                    eachFile {
                        it.name = "rename"
                        assert !(delegate instanceof ${ExtensionAware.name})
                    }
                }
            }
        """

        when:
        succeeds "copy"

        then:
        file("out/rename").exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2838")
    def "include empty dirs works when nested"() {
        given:
        file("a/a.txt") << "foo"
        file("a/dirA").createDir()
        file("b/b.txt") << "foo"
        file("b/dirB").createDir()

        buildScript """
            task copyTask(type: Copy) {
                into "out"
                from "b", {
                    includeEmptyDirs = false
                }
                from "a"
                from "c", {}
            }
        """

        when:
        succeeds "copyTask"

        then:
        executedAndNotSkipped(":copyTask")
        def destinationDir = file("out")
        destinationDir.assertHasDescendants("a.txt", "b.txt", "dirA")
        destinationDir.listFiles().findAll { it.directory }*.name.toSet() == ["dirA"].toSet()
    }

    def "include empty dirs is overridden by subsequent"() {
        given:
        file("a/a.txt") << "foo"
        file("a/dirA").createDir()
        file("b/b.txt") << "foo"
        file("b/dirB").createDir()


        buildScript """
            task copyTask(type: Copy) {
                into "out"
                from "b", {
                    includeEmptyDirs = false
                }
                from "a"
                from "c", {}
                from "b", {
                    includeEmptyDirs = true
                }
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        """

        when:
        succeeds "copyTask"

        then:
        executedAndNotSkipped(":copyTask")

        def destinationDir = file("out")
        destinationDir.assertHasDescendants("a.txt", "b.txt", "dirA", "dirB")
        destinationDir.listFiles().findAll { it.directory }*.name.toSet() == ["dirA", "dirB"].toSet()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2902")
    def "internal copy spec methods are not visible to users"() {
        when:
        file("res/foo.txt") << "bar"

        buildScript """
            task copyAction {
                ext.source = 'res'
                doLast {
                    copy {
                        from source
                        into 'action'
                    }
                }
            }
            task copyTask(type: Copy) {
                ext.children = 'res'
                into "task"
                into "dir", {
                    from children
                }
            }
        """

        then:
        succeeds "copyAction", "copyTask"

        and:
        file("action/foo.txt").exists()
        file("task/dir/foo.txt").exists()
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3022")
    def "filesMatching must match against sourcePath"() {
        given:
        file("a/b.txt") << "\$foo"

        when:
        buildScript """
           task c(type: Copy) {
               from("a") {
                   filesMatching("b.txt") {
                       expand foo: "bar"
                   }
                   into "nested"
               }
               into "out"
           }
        """

        then:
        succeeds "c"

        and:
        file("out/nested/b.txt").text == "bar"
    }

    @Issue("GRADLE-3418")
    def "can copy files with #filePath in path when excluding #pattern"() {
        given:
        file("test/${filePath}/a.txt").touch()

        buildScript """
            task copy(type: Copy) {
                into "out"
                from "test"
                exclude "$pattern"
            }
        """

        when:
        succeeds "copy"

        then:
        file("out/${filePath}/a.txt").exists()

        where:
        pattern      | filePath
        "**/#*#"     | "#"
        "**/%*%"     | "%"
        "**/abc*abc" | "abc"
    }

    def "changing case-sensitive setting makes task out-of-date"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               caseSensitive = providers.systemProperty('case-sensitive').present
               from 'src'
               into 'dest'
               include '**/sub/**'
               exclude '**/ignore/**'
            }
        '''.stripIndent()
        run 'copy'

        when:
        run "copy"

        then:
        skipped(':copy')

        when:
        run "copy", "-Dcase-sensitive"

        then:
        executedAndNotSkipped(':copy')

        when:
        run "copy", "-Dcase-sensitive"

        then:
        skipped(':copy')
    }

    @ToBeImplemented
    @Issue("https://issues.gradle.org/browse/GRADLE-1276")
    def "changing expansion makes task out-of-date"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               expand(one: 1)
            }
        '''.stripIndent()
        run 'copy'

        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               expand(one: 2)
            }
        '''.stripIndent()
        when:
        run "copy"
        then:
        // TODO Task should not be skipped
        !!!skipped(":copy")
    }

    @ToBeImplemented
    @Issue("https://issues.gradle.org/browse/GRADLE-1298")
    def "changing filter makes task out-of-date"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               filter { it.contains '$one' }
            }
        '''.stripIndent()
        run 'copy'

        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               filter { it.contains '$two' }
            }
        '''.stripIndent()
        when:
        run "copy"
        then:
        // TODO Task should not be skipped
        !!!skipped(":copy")
    }

    @ToBeImplemented
    @Issue("https://issues.gradle.org/browse/GRADLE-3549")
    def "changing rename makes task out-of-date"() {
        given:
        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               rename '(.*).a', '\$1.renamed'
            }
        '''.stripIndent()
        run 'copy'

        buildScript '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               rename '(.*).a', '\$1.moved'
            }
        '''.stripIndent()
        when:
        run "copy"
        then:
        // TODO Task should not be skipped
        !!!skipped(":copy")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3554")
    def "copy with dependent task executes dependencies"() {
        given:
        buildScript '''
            apply plugin: "war"

            task copy(type: Copy) {
                from 'src'
                into 'dest'
                with tasks.war
            }
        '''.stripIndent()

        when:
        run 'copy'
        then:
        result.assertTasksExecuted(":compileJava", ":processResources", ":classes", ":copy")
    }

    def "changing spec-level property #property makes task out-of-date"() {
        given:
        buildScript """
            task (copy, type:Copy) {
               from ('src') {
                  def newValue = providers.systemProperty('new-value').present
                  if (newValue) {
                        $newValue
                  } else {
                        $oldValue
                  }
               }
               into 'dest'
            }
        """

        run 'copy'

        when:
        run 'copy'

        then:
        skipped(':copy')

        when:
        run "copy", "--info", "-Dnew-value"

        then:
        executedAndNotSkipped(':copy')
        output.contains "Value of input property 'rootSpec\$1\$1.$property' has changed for task ':copy'"

        when:
        run "copy", "--info", "-Dnew-value"

        then:
        skipped(':copy')

        where:
        property             | oldValue                                          | newValue
        "caseSensitive"      | "caseSensitive = false"                           | "caseSensitive = true"
        "includeEmptyDirs"   | "includeEmptyDirs = false"                        | "includeEmptyDirs = true"
        "duplicatesStrategy" | "duplicatesStrategy = DuplicatesStrategy.EXCLUDE" | "duplicatesStrategy = DuplicatesStrategy.INCLUDE"
        "dirPermissions"     | "dirPermissions { unix(\"0700\") }"               | "dirPermissions { unix(\"0755\") }"
        "filePermissions"    | "filePermissions { unix(\"0600\") }"              | "filePermissions { unix(\"0644\") }"
        "filteringCharset"   | "filteringCharset = 'iso8859-1'"                  | "filteringCharset = 'utf-8'"
    }

    def "null action is forbidden for #method"() {
        given:
        buildScript """
            task copy(type: Copy) {
                into "out"
                from 'src'
                ${method} 'dest', null
            }
        """

        expect:
        fails 'copy'
        failure.assertHasCause("Gradle does not allow passing null for the configuration action for CopySpec.${method}().")

        where:
        method << ["from", "into"]
    }

    @ToBeFixedForConfigurationCache(
        because = "eachFile, expand, filter and rename",
        skip = ToBeFixedForConfigurationCache.Skip.FLAKY
    )
    def "task output caching is disabled when #description is used"() {
        file("src.txt").createFile()
        buildFile << """
            task copy(type: Copy) {
                outputs.cacheIf { true }
                ${mutation}
                from "src.txt"
                into "destination"
            }
        """

        withBuildCache().run "copy"
        file("destination").deleteDir()

        when:
        withBuildCache().run "copy"

        then:
        noneSkipped()

        where:
        description                 | mutation
        "outputs.cacheIf { false }" | "outputs.cacheIf { false }"
        "eachFile(Closure)"         | "eachFile {}"
        "eachFile(Action)"          | "eachFile(org.gradle.internal.Actions.doNothing())"
        "expand(Map)"               | "expand([:])"
        "filter(Closure)"           | "filter {}"
        "filter(Class)"             | "filter(PushbackReader)"
        "filter(Map, Class)"        | "filter([:], PushbackReader)"
        "filter(Transformer)"       | "filter(org.gradle.internal.Transformers.noOpTransformer())"
        "rename(Closure)"           | "rename {}"
        "rename(Pattern, String)"   | "rename(/(.*)/, '\$1')"
        "rename(Transformer)"       | "rename(org.gradle.internal.Transformers.noOpTransformer())"
    }

}
