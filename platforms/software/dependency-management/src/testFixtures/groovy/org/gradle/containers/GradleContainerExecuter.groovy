/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.containers

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.test.fixtures.file.TestDirectoryProvider

/**
 * An executer which executes builds in a container.
 * The implementation is incomplete: if a feature is missing or doesn't work
 * as you expect, it's probably because it's not implemented yet.
 */
@CompileStatic
class GradleContainerExecuter extends AbstractGradleExecuter {
    private final GradleInContainer container

    GradleContainerExecuter(GradleInContainer container, GradleDistribution distribution, TestDirectoryProvider provider) {
        super(distribution, provider)
        this.container = container
    }

    @Override
    protected ExecutionResult doRun() {
        ExecutionResult result = executeInContainer()
        if (result instanceof ExecutionFailure) {
            throw new AssertionError("Expected the build to pass but it failed")
        }
        result
    }

    @CompileDynamic
    private OutputScrapingExecutionResult executeInContainer() {
        def invocation = buildInvocation()
        container.withEnv(invocation.environmentVars.collectEntries { k, v -> [k, v]})
        def containerResult = container.execute(["/gradle-under-test/bin/gradle", *invocation.args] as String[])
        def result
        if (containerResult.exitCode == 0) {
            result = OutputScrapingExecutionResult.from(containerResult.stdOut, containerResult.stdErr)
        } else {
            result = OutputScrapingExecutionFailure.from(containerResult.stdOut, containerResult.stdErr)
        }
        return result
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        ExecutionResult result = executeInContainer()
        if (!(result instanceof ExecutionFailure)) {
            throw new AssertionError("Expected build to fail but it passed")
        }
        (ExecutionFailure) result
    }

    @Override
    void assertCanExecute() throws AssertionError {

    }
}
