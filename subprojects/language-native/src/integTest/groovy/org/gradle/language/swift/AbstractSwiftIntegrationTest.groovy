/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift

import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.SourceElement
import org.gradle.nativeplatform.fixtures.app.Swift3
import org.gradle.nativeplatform.fixtures.app.Swift4
import org.gradle.util.Matchers

@RequiresInstalledToolChain(ToolChainRequirement.SWIFT)
abstract class AbstractSwiftIntegrationTest extends AbstractSwiftComponentIntegrationTest {
    def "skip assemble tasks when no source"() {
        given:
        makeSingleProject()

        expect:
        succeeds "assemble"
        result.assertTasksExecuted(tasksToAssembleDevelopmentBinary, ":assemble")
        // TODO - should skip the task as NO-SOURCE
        result.assertTasksSkipped(tasksToAssembleDevelopmentBinary, ":assemble")
    }

    def "build fails when compilation fails"() {
        given:
        makeSingleProject()

        and:
        file("src/main/swift/broken.swift") << "broken!"

        expect:
        fails "assemble"
        failure.assertHasDescription("Execution failed for task '$developmentBinaryCompileTask'.")
        failure.assertHasCause("A build operation failed.")
        failure.assertThatCause(Matchers.containsText("Swift compiler failed while compiling swift file(s)"))
    }

    protected abstract List<String> getTasksToAssembleDevelopmentBinary()

    @Override
    SourceElement getSwift3Component() {
        return new Swift3('project')
    }

    @Override
    SourceElement getSwift4Component() {
        return new Swift4('project')
    }

    @Override
    String getTaskNameToAssembleDevelopmentBinary() {
        return "assemble"
    }

    @Override
    String getDevelopmentBinaryCompileTask() {
        return ":compileDebugSwift"
    }

    @Override
    List<String> getTasksToAssembleDevelopmentBinaryOfComponentUnderTest() {
        return getTasksToAssembleDevelopmentBinary()
    }
}
