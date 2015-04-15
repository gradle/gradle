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
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.launcher.daemon.configuration.GradleProperties
import org.gradle.model.internal.persist.ReusingModelRegistryStore

@CompileStatic
@EqualsAndHashCode
class GradleInvocationSpec {

    final GradleDistribution gradleDistribution
    final File workingDirectory
    final List<String> tasksToRun
    final List<String> args
    final List<String> gradleOpts
    final boolean useDaemon
    final boolean useToolingApi

    GradleInvocationSpec(GradleDistribution gradleDistribution, File workingDirectory, List<String> tasksToRun, List<String> args, List<String> gradleOpts, boolean useDaemon, boolean useToolingApi) {
        this.gradleDistribution = gradleDistribution
        this.workingDirectory = workingDirectory
        this.tasksToRun = tasksToRun
        this.args = args
        this.gradleOpts = gradleOpts
        this.useDaemon = useDaemon
        this.useToolingApi = useToolingApi
    }

    static Builder builder() {
        return new Builder()
    }

    GradleInvocationSpec withAdditionalGradleOpts(List<String> additionalGradleOpts) {
        return new GradleInvocationSpec(gradleDistribution, workingDirectory, tasksToRun, args, ImmutableList.builder().addAll(gradleOpts).addAll(additionalGradleOpts).build(), useDaemon, useToolingApi)
    }

    GradleInvocationSpec withAdditionalArgs(List<String> additionalArgs) {
        return new GradleInvocationSpec(gradleDistribution, workingDirectory, tasksToRun, ImmutableList.builder().addAll(args).addAll(additionalArgs).build(), gradleOpts, useDaemon, useToolingApi)
    }

    static class Builder {
        GradleDistribution gradleDistribution
        File workingDirectory
        List<String> tasksToRun = []
        List<String> args = []
        List<String> gradleOptions = []
        boolean useDaemon
        boolean useToolingApi

        Builder distribution(GradleDistribution gradleDistribution) {
            this.gradleDistribution = gradleDistribution
            this
        }

        Builder workingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory
            this
        }

        Builder tasksToRun(String... taskToRun) {
            this.tasksToRun.addAll(Arrays.asList(taskToRun))
            this
        }

        Builder args(String... args) {
            this.args.addAll(Arrays.asList(args))
            this
        }

        Builder gradleOpts(String... gradleOpts) {
            this.gradleOptions.addAll(Arrays.asList(gradleOpts))
            this
        }

        Builder useDaemon() {
            useDaemon(true)
        }

        Builder useDaemon(boolean flag) {
            this.useDaemon = flag
            this
        }

        Builder useToolingApi() {
            useToolingApi(true)
        }

        Builder useToolingApi(boolean flag) {
            this.useToolingApi = flag
            this
        }

        Builder enableModelReuse() {
            gradleOpts("-D$ReusingModelRegistryStore.TOGGLE=true")
        }

        Builder disableDaemonLogging() {
            gradleOpts("-Dorg.gradle.daemon.disable-output=true")
        }

        Builder enableTransformedModelDsl() {
            gradleOpts("-Dorg.gradle.model.dsl=true")
        }

        Builder disableParallelWorkers() {
            gradleOpts("-D${GradleProperties.WORKERS_PROPERTY}=1")
        }

        GradleInvocationSpec build() {
            assert gradleDistribution != null
            assert workingDirectory != null

            return new GradleInvocationSpec(gradleDistribution, workingDirectory, tasksToRun.asImmutable(), args.asImmutable(), gradleOptions.asImmutable(), useDaemon, useToolingApi)
        }

    }
}
