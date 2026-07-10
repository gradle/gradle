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

package org.gradle.launcher.exec

import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.buildtree.BuildTreeLifecycleController
import spock.lang.Specification

class ChainingBuildActionRunnerTest extends Specification {
    def runner1 = Mock(BuildActionRunner)
    def runner2 = Mock(BuildActionRunner)
    def runner3 = Mock(BuildActionRunner)
    def runner = new ChainingBuildActionRunner([runner1, runner2, runner3])

    def "invokes runners until a result is produced"() {
        def action = Stub(BuildAction)
        def controller = Mock(BuildTreeLifecycleController)

        when:
        runner.run(action, controller)

        then:
        1 * runner1.run(action, controller) >> BuildActionRunner.Result.nothing()
        1 * runner2.run(action, controller) >> BuildActionRunner.Result.of("thing")
        0 * _
    }

    def "invokes runners until a failure is produced"() {
        def action = Stub(BuildAction)
        def controller = Mock(BuildTreeLifecycleController)

        when:
        runner.run(action, controller)

        then:
        1 * runner1.run(action, controller) >> BuildActionRunner.Result.nothing()
        1 * runner2.run(action, controller) >> BuildActionRunner.Result.failed(new RuntimeException("broken"))
        0 * _
    }
}
