/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.configuration


import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.invocation.NotifyRootProjectBuildOperationType

class NotifyRootProjectBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def initFile = file('init.gradle')

    private void run() {
        def args = initFile.exists() ? ['-I', initFile.name, 'help'] : ['help']
        succeeds(*args)

        // useful for inspecting ops when things go wrong
        operations.debugTree({ op -> !op.hasDetailsOfType(RegisterTaskBuildOperationType.Details) })
    }

    def 'rootProject notification emits build operation'() {
        given:
        initFile << "rootProject { }"
        file('buildSrc/build.gradle') << ''
        file('included/build.gradle') << ''
        file('settings.gradle') << "includeBuild './included'"

        when:
        run()

        then:
        verifyExpectedNotifyRootProjectOp(':')
        verifyExpectedNotifyRootProjectOp(':buildSrc')
        verifyExpectedNotifyRootProjectOp(':included')
    }

    private void verifyExpectedNotifyRootProjectOp(String buildPath) {
        def op = operations.only(NotifyRootProjectBuildOperationType, { it.details.buildPath == buildPath })
        assert op.displayName == "Notify rootProject listeners"
        assert op.children*.displayName == ["Execute 'rootProject {}' action"]
    }

}
