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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.junit.Test

class MultiprojectIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void canInjectConfigurationFromParentProject() {
        createDirs("a", "b")
        testFile('settings.gradle') << 'include "a", "b"'
        testFile('build.gradle') << '''
            allprojects {
                def destDir = buildDir
                task test {
                    doLast {
                        destDir.mkdirs()
                        new File(destDir, 'test.txt') << 'content'
                    }
                }
                gradle.taskGraph.whenReady {
                    destDir.mkdirs()
                    new File(destDir, 'whenReady.txt') << 'content'
                }
                afterEvaluate {
                    destDir.mkdirs()
                    new File(destDir, 'afterEvaluate.txt') << 'content'
                }
            }
'''
        inTestDirectory().withArgument("--no-problems-report").withTasks('test').run()

        testFile('build').assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
        testFile('a/build').assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
        testFile('b/build').assertHasDescendants('test.txt', 'whenReady.txt', 'afterEvaluate.txt')
    }
}
