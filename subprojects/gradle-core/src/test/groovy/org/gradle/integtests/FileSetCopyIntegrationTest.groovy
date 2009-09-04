package org.gradle.integtests

import org.junit.Test

public class FileSetCopyIntegrationTest extends AbstactCopyIntegrationTest {

    @Test public void testCopyWithClosure() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree {
                      from 'src'
                      exclude '**/ignore/**'
                      exclude '**/.svn/**'
                   }.copy { into 'dest'}
                }"""
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        assertFilesExist(
                'dest/one/one.a',
                'dest/one/one.b',
                'dest/two/two.a',
                'dest/two/two.b',
        )
        assertIgnoredMissing()
    }

    @Test public void testCopyWithMap() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree(baseDir:'src', excludes:['**/ignore/**','**/.svn/**']).copy { into 'dest'}
                }"""
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        assertFilesExist(
                'dest/one/one.a',
                'dest/one/one.b',
                'dest/two/two.a',
                'dest/two/two.b',
        )
        assertIgnoredMissing()
    }

    @Test public void testCopyFluent() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree(baseDir:'src').exclude(['**/ignore/**','**/.svn/**']).copy { into 'dest' }
                }"""
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        assertFilesExist(
                'dest/one/one.a',
                'dest/one/one.b',
                'dest/two/two.a',
                'dest/two/two.b',
        )
        assertIgnoredMissing()
    }

    private void assertIgnoredMissing() {
        assertFilesMissing(
                'dest/one/ignore/one.a',
                'dest/one/ignore/one.b',
                'dest/two/ignore/two.a',
                'dest/two/ignore/two.b',
        )
    }



     //todo - does not work yet
//    @Test public void testCopyFromProject() {
//        TestFile buildFile = testFile("build.gradle").writelns(
//                """task cpy << {
//                   copy {
//                        from fileSet(dir:'src', excludes:['**/ignore/**','**/.svn/**'])
//                        into 'dest'
//                    }
//                }"""
//        )
//        usingBuildFile(buildFile).withTasks("cpy").run()
//        assertFilesExist(
//                'dest/one/one.a',
//                'dest/one/one.b',
//                'dest/two/two.a',
//                'dest/two/two.b',
//        )
//        assertIgnoredMissing()
//    }
}