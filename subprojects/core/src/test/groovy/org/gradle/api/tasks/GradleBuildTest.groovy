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
import org.gradle.initialization.GradleLauncher
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

public class GradleBuildTest extends Specification {
    GradleLauncherFactory launcherFactory = Mock()
    GradleBuild task = TestUtil.createTask(GradleBuild, [gradleLauncherFactory: launcherFactory])

    void usesCopyOfCurrentBuildsStartParams() {
        def expectedStartParameter = task.project.gradle.startParameter.newBuild()
        expectedStartParameter.currentDir = task.project.projectDir

        expect:
        task.startParameter == expectedStartParameter

        when:
        task.tasks = ['a', 'b']

        then:
        task.tasks == ['a', 'b']
        task.startParameter.taskNames == ['a', 'b']
    }

    void executesBuild() {
        GradleLauncher launcher = Mock()
        BuildResult resultMock = Mock()

        when:
        task.build()

        then:
        1 * launcherFactory.newInstance(task.startParameter) >> launcher
        1 * launcher.run() >> resultMock
        1 * launcher.stop()
        0 * _._
    }

    void cleansUpOnBuildFailure() {
        GradleLauncher launcher = Mock()
        def failure = new RuntimeException()

        when:
        task.build()

        then:
        RuntimeException e = thrown()
        e == failure
        1 * launcherFactory.newInstance(task.startParameter) >> launcher
        1 * launcher.run() >> { throw failure }
        1 * launcher.stop()
        0 * _._
    }
}
