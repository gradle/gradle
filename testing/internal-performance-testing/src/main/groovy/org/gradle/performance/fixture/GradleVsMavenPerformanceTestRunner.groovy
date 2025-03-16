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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.results.DataReporter
import org.gradle.performance.results.GradleVsMavenBuildPerformanceResults
import org.gradle.performance.util.Git
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.util.GradleVersion

@CompileStatic
class GradleVsMavenPerformanceTestRunner extends AbstractCrossBuildPerformanceTestRunner<GradleVsMavenBuildPerformanceResults> {

    final M2Installation m2

    List<String> gradleTasks
    List<String> equivalentMavenTasks
    List<Object> jvmOpts = []
    List<Object> mvnArgs = []

    int warmUpRuns = 4
    int runs = 12

    GradleVsMavenPerformanceTestRunner(TestDirectoryProvider testDirectoryProvider,
                                       GradleVsMavenBuildExperimentRunner experimentRunner,
                                       DataReporter<GradleVsMavenBuildPerformanceResults> dataReporter,
                                       IntegrationTestBuildContext buildContext) {
        super(experimentRunner, dataReporter, buildContext)
        m2 = new M2Installation(testDirectoryProvider)
    }

    @Override
    GradleVsMavenBuildPerformanceResults run() {
        def commonBaseDisplayName = "${gradleTasks.join(' ')} on $testProject"
        baseline {
            warmUpCount = warmUpRuns
            invocationCount = runs
            projectName(testProject).displayName("Gradle $commonBaseDisplayName").invocation {
                tasksToRun(gradleTasks).jvmArgs(jvmOpts.collect { it.toString() })
            }
        }
        mavenBuildSpec {
            warmUpCount = warmUpRuns
            invocationCount = runs
            projectName(testProject).displayName("Maven $commonBaseDisplayName").invocation {
                tasksToRun(equivalentMavenTasks).mavenOpts(jvmOpts.collect { it.toString() }).args(mvnArgs.collect { it.toString() })
            }
        }
        super.run()
    }

    protected void mavenBuildSpec(@DelegatesTo(MavenBuildExperimentSpec.MavenBuilder) Closure<?> configureAction) {
        configureAndAddSpec(MavenBuildExperimentSpec.builder(), configureAction)
    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        super.finalizeSpec(builder)
        if (builder instanceof MavenBuildExperimentSpec.MavenBuilder) {
            finalizeMavenBuildSpec(builder)
        }
    }

    private void finalizeMavenBuildSpec(MavenBuildExperimentSpec.MavenBuilder builder) {
        def invocation = builder.invocation
        invocation.jvmOpts = customizeJvmOptions(invocation.jvmOpts)
        if (!invocation.args.find { it.startsWith("-Dmaven.repo.local=") }) {
            def localRepoPath = m2.mavenRepo().rootDir.absolutePath
            if (OperatingSystem.current().isWindows()) {
                localRepoPath = localRepoPath.replace("\\", "\\\\").replace(" ", "\\ ")
                invocation.args.add("-Dmaven.repo.local=${localRepoPath}".toString())
            } else {
                invocation.args.add("-Dmaven.repo.local=${localRepoPath}".toString())
            }
        }
        if (!builder.displayName.startsWith("Maven ")) {
            throw new IllegalArgumentException("Maven invocation display name must start with 'Maven '")
        }
    }

    @Override
    protected void finalizeGradleSpec(GradleBuildExperimentSpec.GradleBuilder builder) {
        super.finalizeGradleSpec(builder)
        if (!builder.displayName.startsWith("Gradle ")) {
            throw new IllegalArgumentException("Gradle invocation display name must start with 'Gradle '")
        }
    }

    @Override
    GradleVsMavenBuildPerformanceResults newResult() {
        new GradleVsMavenBuildPerformanceResults(
            testClass: testClassName,
            testId: testId,
            testProject: testProject,
            testGroup: testGroup,
            jvm: Jvm.current().toString(),
            host: InetAddress.getLocalHost().getHostName(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId],
            startTime: clock.getCurrentTime(),
            channel: determineChannel()
        )
    }
}
