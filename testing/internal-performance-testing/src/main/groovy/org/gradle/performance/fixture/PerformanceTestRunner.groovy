/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import org.gradle.performance.results.PerformanceTestResult
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import java.util.function.Function

@CompileStatic
interface PerformanceTestRunner<R extends PerformanceTestResult> {

    void addInterceptor(ExecutionInterceptor interceptor)

    void addBuildMutator(Function<InvocationSettings, BuildMutator> buildMutator)

    String getTestId()

    void setTestId(String testId)

    String getTestClassName()

    void setTestClassName(String testClassName)

    R run()

    /**
     * Modifies a runner prior to test execution
     */
    interface ExecutionInterceptor {
        void intercept(GradleBuildExperimentSpec.GradleBuilder gradleBuilder)

        default void handleFailure(Throwable failure, BuildExperimentSpec spec) {}
    }

}
