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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.launcher.daemon.configuration.GradleProperties

@CompileStatic
@EqualsAndHashCode
class GradleInvocationSpec implements InvocationSpec {

    final GradleDistribution gradleDistribution
    final File workingDirectory
    final List<String> tasksToRun
    final List<String> args
    final List<String> jvmOpts
    final boolean useDaemon
    final boolean useToolingApi
    final boolean expectFailure

    GradleInvocationSpec(GradleDistribution gradleDistribution, File workingDirectory, List<String> tasksToRun, List<String> args, List<String> jvmOpts, boolean useDaemon, boolean useToolingApi, boolean expectFailure) {
        this.gradleDistribution = gradleDistribution
        this.workingDirectory = workingDirectory
        this.tasksToRun = tasksToRun
        this.args = args
        this.jvmOpts = jvmOpts
        this.useDaemon = useDaemon
        this.useToolingApi = useToolingApi
        this.expectFailure = expectFailure
    }

    boolean getBuildWillRunInDaemon() {
        return useDaemon || useToolingApi
    }

    static InvocationBuilder builder() {
        return new InvocationBuilder()
    }

    InvocationBuilder withBuilder() {
        InvocationBuilder builder = new InvocationBuilder()
        builder.distribution(gradleDistribution)
        builder.workingDirectory(workingDirectory)
        builder.tasksToRun.addAll(this.tasksToRun)
        builder.args.addAll(args)
        builder.gradleOptions.addAll(jvmOpts)
        builder.useDaemon = useDaemon
        builder.useToolingApi = useToolingApi
        builder.expectFailure = expectFailure
        builder
    }

    GradleInvocationSpec withAdditionalJvmOpts(List<String> additionalJvmOpts) {
        InvocationBuilder builder = withBuilder()
        builder.gradleOptions.addAll(additionalJvmOpts)
        return builder.build()
    }

    GradleInvocationSpec withAdditionalArgs(List<String> additionalArgs) {
        InvocationBuilder builder = withBuilder()
        builder.args.addAll(additionalArgs)
        return builder.build()
    }

    static class InvocationBuilder implements InvocationSpec.Builder {
        Profiler profiler = new YourKitProfiler()
        GradleDistribution gradleDistribution
        File workingDirectory
        List<String> tasksToRun = []
        List<String> args = []
        List<String> gradleOptions = []
        Map<String, Object> profilerOpts = [:]
        boolean useDaemon
        boolean useToolingApi
        boolean useProfiler
        boolean expectFailure


        InvocationBuilder distribution(GradleDistribution gradleDistribution) {
            this.gradleDistribution = gradleDistribution
            this
        }

        InvocationBuilder workingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory
            this
        }

        InvocationBuilder tasksToRun(String... taskToRun) {
            this.tasksToRun.addAll(Arrays.asList(taskToRun))
            this
        }

        InvocationBuilder tasksToRun(Iterable<String> taskToRun) {
            this.tasksToRun.addAll(taskToRun)
            this
        }

        InvocationBuilder args(String... args) {
            this.args.addAll(Arrays.asList(args))
            this
        }

        InvocationBuilder gradleOpts(String... gradleOpts) {
            this.gradleOptions.addAll(Arrays.asList(gradleOpts))
            this
        }

        InvocationBuilder useDaemon() {
            useDaemon(true)
        }

        InvocationBuilder useDaemon(boolean flag) {
            this.useDaemon = flag
            this
        }

        InvocationBuilder useToolingApi() {
            useToolingApi(true)
            // Can't use tooling API with profiler yet
            assert !isUseProfiler()
            this
        }

        InvocationBuilder useToolingApi(boolean flag) {
            this.useToolingApi = flag
            this
        }

        InvocationBuilder disableParallelWorkers() {
            gradleOpts("-D${GradleProperties.WORKERS_PROPERTY}=1")
        }

        InvocationBuilder useProfiler() {
            useProfiler = true
            // Can't use tooling API with profiler yet
            assert !isUseToolingApi()
            this
        }

        InvocationBuilder useProfiler(Profiler profiler) {
            useProfiler()
            this.profiler = profiler
            this
        }

        InvocationBuilder profilerOpts(Map<String, Object> profilerOpts) {
            this.profilerOpts.putAll(profilerOpts)
            this
        }

        InvocationBuilder buildInfo(String displayName, String projectName) {
            this.profilerOpts.put("sessionname", "$projectName $displayName".replace(' ', "_").toString())
            this
        }

        @Override
        InvocationSpec.Builder expectFailure() {
            expectFailure = true
            this
        }

        GradleInvocationSpec build() {
            assert gradleDistribution != null
            assert workingDirectory != null

            profiler.addProfilerDefaults(this)
            Set<String> jvmOptsSet = new LinkedHashSet<String>()
            jvmOptsSet.addAll(gradleOptions)
            if (useProfiler) {
                jvmOptsSet.addAll(profiler.profilerArguments(profilerOpts))
            }

            return new GradleInvocationSpec(gradleDistribution, workingDirectory, tasksToRun.asImmutable(), args.asImmutable(), new ArrayList<String>(jvmOptsSet).asImmutable(), useDaemon, useToolingApi, expectFailure)
        }

    }
}
