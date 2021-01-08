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

package org.gradle.performance.fixture;

import groovy.transform.CompileStatic;
import org.gradle.performance.results.GradleProfilerReporter;
import org.gradle.performance.results.MeasuredOperationList;

@CompileStatic
public class GradleVsMavenBuildExperimentRunner extends AbstractBuildExperimentRunner {
    private final GradleBuildExperimentRunner gradleRunner;
    private final MavenBuildExperimentRunner mavenRunner;

    public GradleVsMavenBuildExperimentRunner(GradleProfilerReporter gradleProfilerReporter) {
        super(gradleProfilerReporter);
        this.gradleRunner = new GradleBuildExperimentRunner(gradleProfilerReporter);
        this.mavenRunner = new MavenBuildExperimentRunner(gradleProfilerReporter);
    }

    @Override
    public void doRun(BuildExperimentSpec experiment, MeasuredOperationList results) {
        if (experiment instanceof MavenBuildExperimentSpec) {
            mavenRunner.doRun(experiment, results);
        } else {
            gradleRunner.doRun(experiment, results);
        }
    }
}
