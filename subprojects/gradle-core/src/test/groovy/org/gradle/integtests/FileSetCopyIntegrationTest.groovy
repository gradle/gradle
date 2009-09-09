package org.gradle.integtests

import org.junit.Test

public class FileSetCopyIntegrationTest extends AbstactCopyIntegrationTest {

    @Test public void testCopyWithClosure() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree {
                      from 'src'
                      exclude '**/ignore/**'
                   }.copy { into 'dest'}
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

    @Test public void testCopyWithMap() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree(dir:'src', excludes:['**/ignore/**']).copy { into 'dest'}
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

    @Test public void testCopyFluent() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """task cpy << {
                   fileTree(dir:'src').exclude('**/ignore/**').copy { into 'dest' }
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
}