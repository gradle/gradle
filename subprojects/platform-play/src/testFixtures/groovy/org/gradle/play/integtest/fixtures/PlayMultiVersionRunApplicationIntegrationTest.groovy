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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.util.VersionNumber

abstract class PlayMultiVersionRunApplicationIntegrationTest extends PlayMultiVersionApplicationIntegrationTest {
    RunningPlayApp runningApp
    GradleHandle build

    def setup() {
        runningApp = new RunningPlayApp(testDirectory)
    }

    def startBuild(tasks) {
        build = executer.withTasks(tasks).withForceInteractive(true).withStdinPipe().noDeprecationChecks().start()
        runningApp.initialize(build)
    }

    static java9AddJavaSqlModuleArgs() {
        if (JavaVersion.current().isJava9Compatible()) {
            return "forkOptions.jvmArgs += ['--add-modules', 'java.sql']"
        } else {
            return ""
        }
    }

    String determineRoutesClassName() {
        return versionNumber >= VersionNumber.parse('2.4.0') ? "router/Routes.class" : "Routes.class"
    }
}
