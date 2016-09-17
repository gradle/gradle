/*
 * Copyright 2009 the original author or authors.
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
import org.junit.Rule
import org.junit.Test

public class FileTreeCopyIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    @Test public void testCopyWithClosure() {
        TestFile buildFile = testFile("build.gradle").writelns(
            """
                task cpy {
                    doLast {
                        fileTree('src') {
                            exclude '**/ignore/**'
                        }.copy { into 'dest'}
                    }
                }
            """
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
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

    @Test public void testCopyWithClosureBaseDir() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
                    task cpy {
                        doLast {
                            fileTree((Object){ 'src' }).exclude('**/ignore/**').copy { into 'dest'}
                        }
                    }
                """
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
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

    @Test public void testCopyWithMap() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
                    task cpy {
                        doLast {
                            fileTree(dir:'src', excludes:['**/ignore/**', '**/sub/**']).copy { into 'dest'}
                        }
                    }
                """
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'root.b',
                'accents.c',
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }

    @Test public void testCopyFluent() {
        TestFile buildFile = testFile("build.gradle").writelns(
                """
                    task cpy {
                        doLast {
                            fileTree(dir:'src').exclude('**/ignore/**', '**/sub/*.?').copy { into 'dest' }
                        }
                    }
                """
        )
        usingBuildFile(buildFile).withTasks("cpy").run()
        testFile('dest').assertHasDescendants(
                'root.a',
                'root.b',
                'accents.c',
                'one/one.a',
                'one/one.b',
                'two/two.a',
                'two/two.b',
        )
    }
}
