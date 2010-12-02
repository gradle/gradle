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

import org.gradle.BuildResult
import org.gradle.GradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.api.internal.AbstractTask
import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

public class GradleBuildTest extends AbstractTaskTest {
    GradleLauncherFactory launcherFactoryMock = context.mock(GradleLauncherFactory.class)
    GradleBuild task

    AbstractTask getTask() {
        return task
    }

    @Before
    void setUp() {
        super.setUp()
        task = createTask(GradleBuild.class)
        GradleLauncher.injectCustomFactory(launcherFactoryMock)
    }

    @After
    void tearDown() {
        GradleLauncher.injectCustomFactory(null)
    }

    @Test
    void usesCopyOfCurrentBuildsStartParams() {
        def expectedStartParameter = project.gradle.startParameter.newBuild()
        expectedStartParameter.currentDir = project.projectDir
        assertThat(task.startParameter, equalTo(expectedStartParameter))
        task.tasks = ['a', 'b']
        assertThat(task.tasks, equalTo(['a', 'b']))
        assertThat(task.startParameter.taskNames, equalTo(['a', 'b']))
    }

    @Test
    void executesBuild() {
        GradleLauncher launcherMock = context.mock(GradleLauncher.class)
        BuildResult resultMock = context.mock(BuildResult.class)

        context.checking {
            one(launcherFactoryMock).newInstance(task.startParameter)
            will(returnValue(launcherMock))
            one(launcherMock).run()
            will(returnValue(resultMock))
            one(resultMock).rethrowFailure()
        }
        task.build()
    }
}
