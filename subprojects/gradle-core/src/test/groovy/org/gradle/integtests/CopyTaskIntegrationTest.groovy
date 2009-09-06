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
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test
    public void testMultipleSourceWithSingleExcludeMultiInclude() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task (copy, type:Copy) {",
                "   from('src/one'){ ",
                "      into 'dest/one'",
                "      include '**/*.a'",
                "   }",
                "   from('src/two'){ ",
                "      into 'dest/two'",
                "      include '**/*.b'",
                "   }",
                "   exclude '**/ignore/**'",
                "}"
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'one/one.a',
                'two/two.b',
        )
    }

    @Test void testRename() {
        TestFile buildFile = testFile("build.gradle").writelns(
                "task (copy, type:Copy) {",
                "   from 'src'",
                "   into 'dest'",
                "   exclude '**/ignore/**'",
                "   rename '(.*).a', '\$1.renamed'",
                "}"
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'one/one.renamed',
                'one/one.b',
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
                   filter { (Integer.parseInt(it) * 10) as String }
                   filter { (Integer.parseInt(it) + 2) as String }
                   from('src/two/two.a') {
                     into 'dest'
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
                        from fileTree(baseDir: 'src', excludes:['**/ignore/**'])
                        into 'dest'
                    }
                }"""
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
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
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test public void testCopyFromCompositeFileCollection() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task copy << {
                   copy {
                        from files('src2') + fileTree { from 'src'; exclude '**/ignore/**' }
                        into 'dest'
                        include '**/*.a'
                    }
                }"""
        )
        usingBuildFile(buildFile).withTasks("copy").run()
        testFile('dest').assertHasDescendants(
                'one/one.a',
                'two/two.a',
                'three/three.a'
        )
    }
}