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
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion;

@CompileStatic
class GradleVsMavenPerformanceTestRunner extends AbstractGradleBuildPerformanceTestRunner<GradleVsMavenBuildPerformanceResults> {

    GradleVsMavenPerformanceTestRunner(GradleVsMavenBuildExperimentRunner experimentRunner, DataReporter<GradleVsMavenBuildPerformanceResults> dataReporter) {
        super(experimentRunner, dataReporter)
    }

    @Override
    protected void defaultSpec(BuildExperimentSpec.Builder builder) {
        super.defaultSpec(builder)
        if (builder instanceof GradleBuildExperimentSpec.GradleBuilder) {
            ((GradleInvocationSpec.InvocationBuilder)builder.invocation).distribution(gradleDistribution)
        }
    }

    public void mavenBuildSpec(@DelegatesTo(MavenBuildExperimentSpec.MavenBuilder) Closure<?> configureAction) {
        configureAndAddSpec(MavenBuildExperimentSpec.builder(), configureAction)
    }

    protected void finalizeSpec(BuildExperimentSpec.Builder builder) {
        super.finalizeSpec(builder)
        if (builder instanceof GradleInvocationSpec) {
            def invocation = (GradleInvocationSpec.InvocationBuilder) builder.invocation
            if (!invocation.gradleOptions) {
                invocation.gradleOptions = ['-Xms2g', '-Xmx2g', '-XX:MaxPermSize=256m']
            }
        } else if (builder instanceof MavenBuildExperimentSpec.MavenBuilder) {
            def invocation = ((MavenBuildExperimentSpec.MavenBuilder) builder).invocation
            invocation.workingDirectory = testProjectLocator.findProjectDir(builder.projectName)
            if (!invocation.mavenHome) {
                def home = System.getProperty("MAVEN_HOME")
                if (home) {
                    invocation.mavenHome(new File(home))
                }
            }
        }
    }

    @Override
    GradleVsMavenBuildPerformanceResults newResult() {
        new GradleVsMavenBuildPerformanceResults(
            testId: testId,
            testGroup: testGroup,
            jvm: Jvm.current().toString(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId],
            testTime: System.currentTimeMillis()
        )
    }

    @Override
    MeasuredOperationList operations(GradleVsMavenBuildPerformanceResults result, BuildExperimentSpec spec) {
        result.buildResult(spec.displayInfo)
    }
}
