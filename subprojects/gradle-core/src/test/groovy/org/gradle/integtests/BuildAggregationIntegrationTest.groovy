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

class BuildAggregationIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void canExecuteAnotherBuildFromBuild() {
        testFile('build.gradle') << '''
            assertThat(gradle.parent, nullValue())
            task build << {
                assertThat(gradle.parent, nullValue())
                def startParam = gradle.startParameter.newBuild()
                startParam.currentDir = file('other')
                startParam.taskNames = ['dostuff']
                GradleLauncher.newInstance(startParam).run().rethrowFailure()
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
}

