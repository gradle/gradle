/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.integtests.fixtures.TargetVersions

/**
 * Tests that task classes compiled with the current version of Gradle are compatible with previous versions.
 */
@TargetVersions("7.0+")
class TaskSubclassingBinaryBackwardsCompatibilityCrossVersionSpec extends AbstractTaskSubclassingBinaryCompatibilityCrossVersionSpec {
    def "can use task subclass using previous Gradle version"() {
        given:
        prepareSubclassingTest(current.version)

        expect:
        version current withTasks 'assemble' inDirectory(file("producer")) run()
        version previous withTasks 'tasks' requireDaemon() requireIsolatedDaemons() run()
    }

    def "task can use all methods declared by Task interface that AbstractTask specialises"() {
        given:
        prepareMethodUseTest(current.version)

        expect:
        version current withTasks 'assemble' inDirectory(file("producer")) run()
        version previous requireDaemon() requireIsolatedDaemons() withTasks 't' run()
    }
}
