package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class CopyTaskIntegrationTest  extends AbstactCopyIntegrationTest {
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
        assertFilesExist(
                'dest/one/one.a',
                'dest/one/one.b',
                'dest/two/two.a',
                'dest/two/two.b',
        )
        assertFilesMissing(
                'dest/one/ignore/bad.file',
                'dest/two/ignore/bad.file'
        )
    }

    @Test
    public void testMultipleSourceWithSingleExcludeMultInclude() {
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
        assertFilesExist(
                'dest/one/one.a',
                'dest/two/two.b',
        )
        assertFilesMissing(
                'dest/one/ignore/bad.file',
                'dest/one/one.b',
                'dest/two/ignore/bad.file',
                'dest/two/two.a'
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
        assertFilesExist(
                'dest/one/one.renamed',
                'dest/one/one.b',
                'dest/two/two.renamed',
                'dest/two/two.b'
        )
        assertFilesMissing(
                'dest/one/one.a',
                'dest/one/ignore/bad.file',
                'dest/two/two.a',
                'dest/two/ignore/bad.file'
        )
    }

    @Test
     public void testCopyAction() {
         TestFile buildFile = testFile("build.gradle").writelns(
                 "task copyIt << {",
                 "copy {",
                 "   from 'src'",
                 "   into 'dest'",
                 "   exclude '**/ignore/**'",
                 "}",
                 "}"
         )
         usingBuildFile(buildFile).withTasks("copyIt").run()
         assertFilesExist(
                 'dest/one/one.a',
                 'dest/one/one.b',
                 'dest/two/two.a',
                 'dest/two/two.b',
         )
         assertFilesMissing(
                 'dest/one/ignore/bad.file',
                 'dest/two/ignore/bad.file'
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
        assertFilesExist(
                'dest/two/one.a',
                'dest/two/two.a',
        )
        assertFilesMissing(
                'dest/one/one.a',
                'dest/one/one.b',
                'dest/two/two.b',
                'dest/one/ignore/bad.file',
                'dest/two/ignore/bad.file'
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
}