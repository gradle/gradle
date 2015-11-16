/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.integtests.fixtures.executer.GradleHandle

abstract class PlayMultiVersionRunApplicationIntegrationTest extends PlayMultiVersionApplicationIntegrationTest {
    RunningPlayApp runningApp
    GradleHandle build

    def setup() {
        runningApp = new RunningPlayApp(testDirectory)
    }

    def startBuild(tasks) {
        build = executer.withTasks(tasks).withForceInteractive(true).withStdinPipe().start()
        runningApp.initialize(build)
    }
}
