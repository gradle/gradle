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

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test

class BuildAggregationIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void canExecuteAnotherBuildFromBuild() {
        dist.testFile('build.gradle') << '''
            assert gradle.parent == null
            task build(type: GradleBuild) {
                dir = 'other'
                tasks = ['dostuff']
                startParameter.searchUpwards = false
            }
'''

        dist.testFile('other/build.gradle') << '''
            assert gradle.parent != null
            task dostuff << {
                assert gradle.parent != null
            }
'''

        executer.withTasks('build').run()
    }

    @Test
    public void treatsBuildSrcProjectAsANestedBuild() {
        dist.testFile('build.gradle') << '''
            assert gradle.parent == null
            task build
'''

        dist.testFile('buildSrc/build.gradle') << '''
            apply plugin: 'java'
            assert gradle.parent != null
            classes << {
                assert gradle.parent != null
            }
'''

        executer.withTasks('build').run()
    }

    @Test
    public void reportsNestedBuildFailure() {
        TestFile other = dist.testFile('other.gradle') << '''
            throw new ArithmeticException('broken')
'''

        dist.testFile('build.gradle') << '''
            task build(type: GradleBuild) {
                buildFile = 'other.gradle'
                startParameter.searchUpwards = false
            }
'''

        ExecutionFailure failure = executer.withTasks('build').runWithFailure()
        failure.assertHasFileName("Build file '${other}'")
        failure.assertHasLineNumber(2)
        failure.assertHasDescription('A problem occurred evaluating root project')
        failure.assertHasCause('broken')
    }

    @Test
    public void reportsBuildSrcFailure() {
        dist.testFile('buildSrc/src/main/java/Broken.java') << 'broken!'
        ExecutionFailure failure = executer.runWithFailure()
        failure.assertHasDescription('Execution failed for task \':compileJava\'')
    }
}
