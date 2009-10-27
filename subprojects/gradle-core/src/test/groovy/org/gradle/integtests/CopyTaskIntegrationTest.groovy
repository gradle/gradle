package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class CopyTaskIntegrationTest extends AbstactCopyIntegrationTest {
    @Test
    public void testSingleSourceWithExclude() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task (copy, type:Copy) {",
                "   from 'src'",
                "   into 'dest'",
                "   exclude '**/ignore/**'",
                "}"
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
    public void testMultipleSourceWithInheritedPatterns() {
        TestFile buildFile = testFile("build.gradle") << '''
            task (copy, type:Copy) {
               into 'dest'
               from('src/one') {
                  into 'one'
                  include '**/*.a'
               }
               from('src/two') {
                  into 'two'
                  include '**/*.b'
               }
               exclude '**/ignore/**'
            }
'''
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'one/one.a',
                'two/two.b',
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
                     include '**/*.a'
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

    @Test void testRename() {
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
                'two/two.a',
                'two/two.b',
        )
    }

    @Test public void copySingleFiles() {
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
     * two.a starts off with "1\n2\n3\n"
     * If these filters are chained in the correct order, you should get 6, 11, and 16
     */
    @Test public void copyMultipleFilterTest() {
        TestFile buildFile = testFile('build.gradle').writelns(
                """task (copy, type:Copy) {
                   into 'dest'
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

    @Test public void testCopyFromFileTree() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   copy {
                        from fileTree(dir: 'src', excludes: ['**/ignore/**'])
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

    @Test public void testCopyFromFileCollection() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task copy << {
                   copy {
                        from files('src')
                        into 'dest'
                        exclude '**/ignore/**'
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

    @Test public void testCopyFromCompositeFileCollection() {
        testFile('a.jar').touch()

        TestFile buildFile = testFile("build.gradle").writelns(
                """
                configurations { compile }
                dependencies { compile files('a.jar') }
                task copy << {
                   copy {
                        from files('src2') + fileTree { from 'src'; exclude '**/ignore/**' } + configurations.compile
                        into 'dest'
                        include '**/*a*'
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
}