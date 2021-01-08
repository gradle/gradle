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

package org.gradle.performance.fixture

import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.gradle.performance.results.BuildDisplayInfo
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import javax.annotation.Nullable
import java.util.function.Function

@CompileStatic
@EqualsAndHashCode
abstract class BuildExperimentSpec {
    final String displayName
    final String projectName
    final File workingDirectory
    @Nullable
    final Integer warmUpCount
    @Nullable
    final Integer invocationCount
    final ImmutableList<Function<InvocationSettings, BuildMutator>> buildMutators

    BuildExperimentSpec(String displayName,
                        String projectName,
                        File workingDirectory,
                        Integer warmUpCount,
                        Integer invocationCount,
                        List<Function<InvocationSettings, BuildMutator>> buildMutators
    ) {
        this.displayName = displayName
        this.projectName = projectName
        this.workingDirectory = workingDirectory
        this.warmUpCount = warmUpCount
        this.invocationCount = invocationCount
        this.buildMutators = ImmutableList.copyOf(buildMutators)
    }

    abstract BuildDisplayInfo getDisplayInfo()

    abstract InvocationSpec getInvocation()

    interface Builder {
        String getDisplayName()

        String getProjectName()

        void setProjectName(String projectName)

        File getWorkingDirectory()

        void setWorkingDirectory(File workingDirectory)

        List<Function<InvocationSettings, BuildMutator>> getBuildMutators()

        InvocationSpec.Builder getInvocation()

        BuildExperimentSpec build()
    }
}
