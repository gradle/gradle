/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.TestResources
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Matchers
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.startsWith
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertThat

public class CopyTaskIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    @Test
    public void testSingleSourceWithIncludeAndExclude() {
        TestFile buildFile = testFile("build.gradle") << '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               include '**/sub/**'
               exclude '**/ignore/**'
            }
'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'one/sub/onesub.a',
                'one/sub/onesub.b'
        )
    }

    @Test
    public void testSingleSourceWithSpecClosures() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task (copy, type:Copy) {",
                "   from 'src'",
                "   into 'dest'",
                "   include { fte -> !fte.file.name.endsWith('b') }",
                "   exclude { fte -> fte.file.name == 'bad.file' }",
                "}"
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'one/one.a',
                'two/two.a',
        )
    }

    @Test
    public void testMultipleSourceWithInheritedPatterns() {
        TestFile buildFile = testFile("build.gradle") << '''
            task (copy, type:Copy) {
               into 'dest'
               from('src/one') {
                  into '1'
                  include '**/*.a'
               }
               from('src/two') {
                  into '2'
                  include '**/*.b'
               }
               exclude '**/ignore/**'
            }
'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                '1/one.a',
                '1/sub/onesub.a',
                '2/two.b',
        )
    }

    @Test
    public void testMultipleSourcesWithInheritedDestination() {
        TestFile buildFile = testFile("build.gradle") << '''
            task (copy, type:Copy) {
               into 'dest'
               into('common') {
                  from('src/one') {
                     into 'a/one'
                     include '*.a'
                  }
                  into('b') {
                     from('src/two') {
                        into 'two'
                        include '**/*.b'
                     }
                  }
               }
            }
'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'common/a/one/one.a',
                'common/b/two/two.b',
        )
    }

    @Test
    void testRename() {
        TestFile buildFile = testFile("build.gradle") << '''
            task (copy, type:Copy) {
               from 'src'
               into 'dest'
               exclude '**/ignore/**'
               rename '(.*).a', '\$1.renamed'
               rename { it.startsWith('one.') ? "renamed_$it" : it }
            }
'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'root.renamed',
                'root.b',
                'one/renamed_one.renamed',
                'one/renamed_one.b',
                'one/sub/onesub.renamed',
                'one/sub/onesub.b',
                'two/two.renamed',
                'two/two.b'
        )
    }

    @Test
    public void testCopyAction() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task copyIt << {",
                "   copy {",
                "      from 'src'",
                "      into 'dest'",
                "      exclude '**/ignore/**'",
                "   }",
                "}"
        )
        usingBuildFile(buildFile).withTasks("copyIt").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'root.b',
                'one/one.a',
                'one/one.b',
                'one/sub/onesub.a',
                'one/sub/onesub.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test
    public void copySingleFiles() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task copyIt << {",
                "   copy {",
                "      from 'src/one/one.a', 'src/two/two.a'",
                "      into 'dest/two'",
                "   }",
                "}"
        )
        usingBuildFile(buildFile).withTasks("copyIt").run()
        testFile('dest').assertHasDescendants(
                'two/one.a',
                'two/two.a',
        )
    }

    /*
     * two.a starts off with "$one\n${one+1}\n${one+1+1}\n"
     * If these filters are chained in the correct order, you should get 6, 11, and 16
     */

    @Test
    public void copyMultipleFilterTest() {
        TestFile buildFile = testFile('build.gradle').writelns(
                """task (copy, type:Copy) {
                   into 'dest'
                   expand(one: 1)
                   filter { (Integer.parseInt(it) * 10) as String }
                   filter { (Integer.parseInt(it) + 2) as String }
                   from('src/two/two.a') {
                     filter { (Integer.parseInt(it) / 2) as String }
                   }
                }
                """
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        Iterator<String> it = testFile('dest/two.a').readLines().iterator()
        assertThat(it.next(), startsWith('6'))
        assertThat(it.next(), startsWith('11'))
        assertThat(it.next(), startsWith('16'))
    }

    @Test
    public void chainedTransformations() {
        def buildFile = testFile('build.gradle') << '''
            task copy(type: Copy) {
                into 'dest'
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
'''
        usingBuildFile(buildFile).withTasks('copy').run()
        testFile('dest').assertHasDescendants(
                'root.renamed_twice',
                'root.b',
                'prefix/one/one.renamed_twice',
                'prefix/one/one.b',
                'prefix/one_sub/onesub.renamed_twice',
                'prefix/one_sub/onesub.b'
        )

        Iterator<String> it = testFile('dest/root.renamed_twice').readLines().iterator()
        assertThat(it.next(), equalTo('[prefix: line 1]'))
        assertThat(it.next(), equalTo('[prefix: line 2]'))
    }

    @Test
    public void testCopyFromFileTree() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   copy {
                        from fileTree(dir: 'src', excludes: ['**/ignore/**'], includes: ['*', '*/*'])
                        into 'dest'
                    }
                }"""
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'root.b',
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test
    public void testCopyFromFileCollection() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task copy << {
                   copy {
                        from files('src')
                        into 'dest'
                        exclude '**/ignore/**'
                        exclude '*/*/*/**'
                    }
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'root.b',
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test
    public void testCopyFromCompositeFileCollection() {
        testFile('a.jar').touch()

        TestFile buildFile = testFile("build.gradle").writelns(
                """
                configurations { compile }
                dependencies { compile files('a.jar') }
                task copy << {
                   copy {
                        from files('src2') + fileTree('src') { exclude '**/ignore/**' } + configurations.compile
                        into 'dest'
                        include { fte -> fte.relativePath.segments.length < 3 && (fte.file.directory || fte.file.name.contains('a')) }
                    }
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'one/one.a',
                'two/two.a',
                'three/three.a',
                'a.jar'
        )
    }

    @Test
    public void testCopyFromTask() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
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
                    }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'out.txt',
                'file1.txt',
                'sub/file2.txt'
        )
    }

    @Test
    public void testCopyFromTaskOutputs() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
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
                        }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'out.txt',
                'file1.txt',
                'sub/file2.txt'
        )
    }

    @Test
    public void testCopyWithCopyspec() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
                def parentSpec = copySpec {
                    from 'src'
                    exclude '**/ignore/**'
                    include '*/*.a'
                    into 'subdir'
                }
                task copy(type: Copy) {
                    into 'dest'
                    with parentSpec
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'subdir/one/one.a',
                'subdir/two/two.a'
        )
    }

    @Test
    public void testTransformWithCopyspec() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
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
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'transformedAgain/transformed/subdir/one/one.a',
                'transformedAgain/transformed/subdir/two/two.a'
        )
    }

    @Test
    @Ignore
    //this does not pass with current implementation
    public void testIncludeExcludeWithCopyspec() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
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
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'subdir/one/one.a',
                'subdir/one/sub/onesub.a',
                'subdir/two/two.a'
        )
    }

    /*
   * two.a starts off with "$one\n${one+1}\n${one+1+1}\n"
   * If these filters are chained in the correct order, you should get 6, 11, and 16
   */

    @Test
    public void testMultipleFilterWithCopyspec() {
        TestFile buildFile = testFile('build.gradle').writelns(
                """
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
                """
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        Iterator<String> it = testFile('dest/two.a').readLines().iterator()
        assertThat(it.next(), startsWith('6'))
        assertThat(it.next(), startsWith('11'))
        assertThat(it.next(), startsWith('16'))
    }

    @Test
    void testRenameWithCopySpec() {
        TestFile buildFile = testFile("build.gradle") << '''
            def parentSpec = copySpec {
               from 'src/one'
               exclude '**/ignore/**'
               rename '(.*).b$', '$1.renamed'
            }
            task (copy, type:Copy) {
               with parentSpec
               into 'dest'
               rename { it.startsWith('one.') ? "renamed_$it" : it }
            }'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'renamed_one.renamed',
                'renamed_one.a',
                'sub/onesub.renamed',
                'sub/onesub.a',
        )
    }

    // can't use TestResources here because Git doesn't support committing empty directories
    @Test
    void emptyDirsAreCopiedByDefault() {
        file("src999", "emptyDir").createDir()
        file("src999", "yet", "another", "veryEmptyDir").createDir()

        // need to include a file in the copy, otherwise copy task says "no source files"
        file("src999", "dummy").createFile()

        def buildFile = testFile("build.gradle") <<
                """
                task copy(type: Copy) {
                    from 'src999'
                    into 'dest'
                }
                """
        usingBuildFile(buildFile).withTasks("copy").run()

        assert file("dest", "emptyDir").isDirectory()
        assert file("dest", "emptyDir").list().size() == 0
        assert file("dest", "yet", "another", "veryEmptyDir").isDirectory()
        assert file("dest", "yet", "another", "veryEmptyDir").list().size() == 0
    }

    @Test
    void emptyDirsAreNotCopiedIfCorrespondingOptionIsSetToFalse() {
        file("src999", "emptyDir").createDir()
        file("src999", "yet", "another", "veryEmptyDir").createDir()

        // need to include a file in the copy, otherwise copy task says "no source files"
        file("src999", "dummy").createFile()

        def buildFile = testFile("build.gradle") <<
                """
                task copy(type: Copy) {
                    from 'src999'
                    into 'dest'

                    includeEmptyDirs = false
                }
                """
        usingBuildFile(buildFile).withTasks("copy").run()

        assert !file("dest", "emptyDir").exists()
        assert !file("dest", "yet", "another", "veryEmptyDir").exists()
    }

    @Test
    public void testCopyExcludeDuplicates() {
        file('dir1', 'path', 'file.txt').createFile() << "f1"
        file('dir2', 'path', 'file.txt').createFile() << "f2"


        def buildFile = testFile('build.gradle') <<
                '''
            task copy(type: Copy) {
                from 'dir1'
                from 'dir2'
                into 'dest'

                eachFile { it.duplicatesStrategy = 'exclude' }
            }

        '''

        def result = usingBuildFile(buildFile).withTasks("copy").run()
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').assertContents(Matchers.containsText("f1"))
        assertFalse(result.output.contains('deprecated'))
    }

    @Test
    public void renamedFileCanBeTreatedAsDuplicate() {
        File file1 = file('dir1', 'path', 'file.txt').createFile()
        File file2 = file('dir2', 'path', 'file2.txt').createFile()
        file1.text = "file1"
        file2.text = "file2"

        def buildFile = testFile('build.gradle') <<
                '''
                task copy(type: Copy) {
                    from 'dir1'
                    from 'dir2'
                    rename 'file2.txt', 'file.txt'
                    into 'dest'

                    eachFile { it.duplicatesStrategy = 'exclude' }
                }
            '''
        def result = usingBuildFile(buildFile).withTasks("copy").run()
        file('dest').assertHasDescendants('path/file.txt')
        file('dest/path/file.txt').assertContents(Matchers.containsText("file1"))
        assertFalse(result.output.contains('deprecated'))
    }

    @Test
    public void testEachChainedMatchingRuleAlwaysMatchesAgainstInitialSourcePath() {
        file('path/abc.txt').createFile().write('test file with $attr')
        file('path/bcd.txt').createFile()

        def buildFile = testFile('build.gradle') <<
                '''
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
            }'''

        usingBuildFile(buildFile).withTasks('copy').run();
        file('dest').assertHasDescendants('bcd.txt', 'abc.txt.concrete')
        file('dest/abc.txt.concrete').text = 'test file with some value'
    }

    @Test
    public void testChainedMatchingRulesDoNotMatchAgainstDestinationPathSetByPreviousChainElement() {
        file('path/abc.txt').createFile().write('test file with $attr')
        file('path/bcd.txt').createFile()

        def buildFile = testFile('build.gradle') <<
                '''
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
            }'''

        usingBuildFile(buildFile).withTasks('copy').run();
        file('dest').assertHasDescendants('bcd.txt', 'abc.txt.template')
        file('dest/abc.txt.template').text = 'test file with some $attr'
    }

    @Test
    public void testAccessSourceNameFromFileCopyDetails() {
        file('path/abc.txt').createFile().write('content')
        file('path/bcd.txt').createFile()

        def buildFile = testFile('build.gradle') <<
                '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    name = "DEST-" + sourceName
                }
            }'''

        usingBuildFile(buildFile).withTasks('copy').run();
        file('dest').assertHasDescendants('bcd.txt', 'DEST-abc.txt')
        file('dest/DEST-abc.txt').text = 'content'
    }

    @Test
    public void testAccessSourcePathFromFileCopyDetails() {
        file('path/abc.txt').createFile().write('content')
        file('path/bcd.txt').createFile()

        def buildFile = testFile('build.gradle') <<
                '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    path = sourcePath.replace('txt', 'log')
                }
            }'''

        usingBuildFile(buildFile).withTasks('copy').run();
        file('dest').assertHasDescendants('bcd.txt', 'abc.log')
        file('dest/abc.log').text = 'content'
    }

    @Test
    public void testAccessRelativeSourcePathFromFileCopyDetails() {
        file('path/abc.txt').createFile().write('content')
        file('path/bcd.txt').createFile()

        def buildFile = testFile('build.gradle') <<
                '''
            task copy(type: Copy) {
                from 'path'
                into 'dest'
                filesMatching ('**/a*') {
                    relativePath = relativeSourcePath.replaceLastName('abc.log')
                }
            }'''

        usingBuildFile(buildFile).withTasks('copy').run();
        file('dest').assertHasDescendants('bcd.txt', 'abc.log')
        file('dest/abc.log').text = 'content'
    }

    @Test
    public void testSingleLineRemoved() {
        TestFile buildFile = testFile('build.gradle') << '''
            task (copy, type:Copy) {
                from "src/two/two.b"
                into "dest"
                def lineNumber = 1
                filter { lineNumber++ % 2 == 0 ? null : it }
            }'''
        usingBuildFile(buildFile).withTasks('copy').run()
        Iterator<String> it = testFile('dest/two.b').readLines().iterator()
        assertThat(it.next(), startsWith('one'))
        assertThat(it.next(), startsWith('three'))
    }

    @Test
    public void testAllLinesRemoved() {
        TestFile buildFile = testFile('build.gradle') << '''
            task (copy, type:Copy) {
                from "src/two/two.b"
                into "dest"
                def lineNumber = 1
                filter { null }
            }'''
        usingBuildFile(buildFile).withTasks('copy').run()
        Iterator<String> it = testFile('dest/two.b').readLines().iterator()
        assertFalse(it.hasNext())
    }
}
