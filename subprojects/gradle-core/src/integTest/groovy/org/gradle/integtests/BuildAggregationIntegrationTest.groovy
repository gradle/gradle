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

import org.junit.Test
import static org.hamcrest.Matchers.*
import org.gradle.util.TestFile

class BuildAggregationIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void canExecuteAnotherBuildFromBuild() {
        testFile('build.gradle') << '''
            assertThat(gradle.parent, nullValue())
            task build(type: GradleBuild) {
                dir = 'other'
                tasks = ['dostuff']
            }
'''

        testFile('other/build.gradle') << '''
            assertThat(gradle.parent, notNullValue())
            task dostuff << {
                assertThat(gradle.parent, notNullValue())
            }
'''

        inTestDirectory().withTasks('build').run()
    }

    @Test
    public void treatsBuildSrcProjectAsANestedBuild() {
        testFile('build.gradle') << '''
            assertThat(gradle.parent, nullValue())
            task build
'''

        testFile('buildSrc/build.gradle') << '''
            usePlugin 'java'
            assertThat(gradle.parent, notNullValue())
            classes << {
                assertThat(gradle.parent, notNullValue())
            }
'''

        inTestDirectory().withTasks('build').run()
    }

    @Test
    public void reportsNestedBuildFailure() {
        TestFile other = testFile('other.gradle') << '''
            1/0
'''

        testFile('build.gradle') << '''
            task build(type: GradleBuild) {
                buildFile = 'other.gradle'
            }
'''

        ExecutionFailure failure = inTestDirectory().withTasks('build').runWithFailure()
        failure.assertHasFileName("Build file '${other}'")
        failure.assertHasLineNumber(2)
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertThatCause(containsString('/ by zero'))
    }

    @Test
    public void reportsBuildSrcFailure() {
        testFile('buildSrc/src/main/java/Broken.java') << 'broken!'
        ExecutionFailure failure = inTestDirectory().runWithFailure()
        failure.assertHasFileName('Default buildSrc build script')
        failure.assertHasDescription('Execution failed for task \':compileJava\'')
    }
}


