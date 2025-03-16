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

package org.gradle.performance.fixture


import org.gradle.performance.generator.JavaTestProjectGenerator
import org.gradle.performance.mutator.ApplyAbiChangeToGroovySourceFileMutator
import org.gradle.performance.mutator.ApplyNonAbiChangeToGroovySourceFileMutator
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.InvocationSettings
import org.gradle.profiler.mutations.ApplyAbiChangeToJavaSourceFileMutator
import org.gradle.profiler.mutations.ApplyNonAbiChangeToJavaSourceFileMutator
import org.gradle.test.fixtures.language.Language

import javax.annotation.Nullable

class JavaTestProject implements IncrementalTestProject {

    static JavaTestProject projectFor(String testProject) {
        def javaTestProject = findProjectFor(testProject)
        if (javaTestProject == null) {
            throw new IllegalArgumentException("Cannot find Java test project for ${testProject}")
        }
        return javaTestProject
    }

    @Nullable
    static JavaTestProject findProjectFor(String testProject) {
        def generator = JavaTestProjectGenerator.values().find { it.projectName == testProject }
        return generator == null ? null : new JavaTestProject(generator)
    }

    @Delegate(interfaces = false)
    private final JavaTestProjectGenerator generator

    private JavaTestProject(JavaTestProjectGenerator generator) {
        this.generator = generator
    }

    @Override
    void configure(CrossVersionPerformanceTestRunner runner) {
        runner.gradleOpts.addAll(getMemoryOptions())
    }

    @Override
    void configure(GradleBuildExperimentSpec.GradleBuilder builder) {
        builder.invocation {
            jvmArgs(memoryOptions)
        }
    }

    private List<String> getMemoryOptions() {
        ["-Xms${generator.daemonMemory}".toString(), "-Xmx${generator.daemonMemory}".toString()]
    }

    @Override
    void configureForAbiChange(CrossVersionPerformanceTestRunner runner) {
        configure(runner)
        runner.tasksToRun = ['assemble']
        runner.addBuildMutator(this.&abiChangeBuildMutator)
    }

    @Override
    void configureForAbiChange(GradleBuildExperimentSpec.GradleBuilder builder) {
        configure(builder)
        builder.invocation {
            tasksToRun = ['assemble']
        }
        builder.addBuildMutator(this.&abiChangeBuildMutator)
    }

    private BuildMutator abiChangeBuildMutator(InvocationSettings invocationSettings) {
        File fileToChange = new File(invocationSettings.projectDir, generator.config.fileToChangeByScenario['assemble'])
        return (generator.config.language == Language.GROOVY) ?
            new ApplyAbiChangeToGroovySourceFileMutator(fileToChange) :
            new ApplyAbiChangeToJavaSourceFileMutator(fileToChange)
    }

    @Override
    void configureForNonAbiChange(CrossVersionPerformanceTestRunner runner) {
        configure(runner)
        runner.tasksToRun = ['assemble']
        runner.addBuildMutator(this.&nonAbiChangeBuildMutator)
    }

    @Override
    void configureForNonAbiChange(GradleBuildExperimentSpec.GradleBuilder builder) {
        configure(builder)
        builder.invocation {
            tasksToRun = ['assemble']
        }
        builder.addBuildMutator(this.&nonAbiChangeBuildMutator)
    }

    private BuildMutator nonAbiChangeBuildMutator(InvocationSettings invocationSettings) {
        File fileToChange = new File(invocationSettings.projectDir, generator.config.fileToChangeByScenario['assemble'])
        return (generator.config.language == Language.GROOVY) ?
            new ApplyNonAbiChangeToGroovySourceFileMutator(fileToChange) :
            new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)
    }
}
