package org.gradle.integtests

import org.junit.Test
import org.junit.Before
import org.apache.commons.io.FileUtils

public class CopyTaskIntegrationTest  extends AbstractIntegrationTest {
    @Before
    public void setUp() {
        File resourceFile = null;
        try {
            resourceFile = new File(getClass().getResource("copyTestResources/src").toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException('Could not locate copy test resources');
        }

        File testSrc = testFile('src')
        FileUtils.deleteQuietly(testSrc)

        FileUtils.copyDirectory(resourceFile, testSrc)
    }

    private void assertFilesExist(String... paths) {
        for (String path: paths) {
            testFile(path).assertExists()
        }
    }

    private void assertFilesMissing(String... paths) {
        for (String path: paths) {
            testFile(path).assertDoesNotExist()
        }
    }

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

}