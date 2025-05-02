/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.performance.results.BuildDisplayInfo
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings

import java.util.function.Function

@CompileStatic
class GradleBuildExperimentSpec extends BuildExperimentSpec {
    final GradleInvocationSpec invocation
    final ImmutableList<String> measuredBuildOperations
    final boolean measureGarbageCollection
    final boolean crossVersion

    GradleBuildExperimentSpec(
        String displayName,
        String projectName,
        File workingDirectory,
        GradleInvocationSpec invocation,
        boolean crossVersion,
        Integer warmUpCount,
        Integer invocationCount,
        ImmutableList<Function<InvocationSettings, BuildMutator>> buildMutators,
        ImmutableList<String> measuredBuildOperations, boolean measureGarbageCollection
    ) {
        super(displayName, projectName, workingDirectory, warmUpCount, invocationCount, buildMutators)
        this.crossVersion = crossVersion
        this.measuredBuildOperations = measuredBuildOperations
        this.measureGarbageCollection = measureGarbageCollection
        this.invocation = invocation
    }

    @Override
    GradleInvocationSpec getInvocation() {
        invocation
    }

    static GradleBuilder builder() {
        new GradleBuilder()
    }

    @Override
    BuildDisplayInfo getDisplayInfo() {
        new BuildDisplayInfo(projectName, displayName, invocation.tasksToRun, invocation.cleanTasks, invocation.args, invocation.jvmArguments, invocation.useDaemon)
    }

    static class GradleBuilder implements BuildExperimentSpec.Builder {
        String displayName
        String projectName
        File workingDirectory
        GradleInvocationSpec.InvocationBuilder invocation = GradleInvocationSpec.builder()
        Integer warmUpCount
        Integer invocationCount
        final List<Function<InvocationSettings, BuildMutator>> buildMutators = []
        final List<String> measuredBuildOperations = []
        boolean measureGarbageCollection
        boolean crossVersion

        GradleBuilder displayName(String displayName) {
            this.displayName = displayName
            this
        }

        GradleBuilder projectName(String projectName) {
            this.projectName = projectName
            this
        }

        GradleBuilder warmUpCount(Integer warmUpCount) {
            this.warmUpCount = warmUpCount
            this
        }

        GradleBuilder invocationCount(Integer invocationCount) {
            this.invocationCount = invocationCount
            this
        }

        GradleBuilder invocation(@DelegatesTo(GradleInvocationSpec.InvocationBuilder) Closure<?> conf) {
            invocation.with(conf as Closure<Object>)
            this
        }

        GradleBuilder buildMutators(List<Function<InvocationSettings, BuildMutator>> mutators) {
            this.buildMutators.clear()
            this.buildMutators.addAll(mutators)
            this
        }

        GradleBuilder addBuildMutator(Function<InvocationSettings, BuildMutator> buildMutator) {
            this.buildMutators.add(buildMutator)
            this
        }

        GradleBuilder measuredBuildOperations(List<String> measuredBuildOperations) {
            this.measuredBuildOperations.clear()
            this.measuredBuildOperations.addAll(measuredBuildOperations)
            this
        }

        GradleBuilder measureGarbageCollection(boolean measureGarbageCollectionTime) {
            this.measureGarbageCollection = measureGarbageCollectionTime
            this
        }

        GradleBuilder crossVersion(boolean crossVersion) {
            this.crossVersion = crossVersion
            this
        }

        BuildExperimentSpec build() {
            assert projectName != null
            assert displayName != null
            assert invocation != null

            new GradleBuildExperimentSpec(
                displayName,
                projectName,
                workingDirectory,
                invocation.build(),
                crossVersion,
                warmUpCount,
                invocationCount,
                ImmutableList.copyOf(buildMutators),
                ImmutableList.copyOf(measuredBuildOperations),
                measureGarbageCollection
            )
        }
    }
}
