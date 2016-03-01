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

import java.io.File;

public class DefaultBuildExperimentInvocationInfo implements BuildExperimentInvocationInfo {
    private final BuildExperimentSpec experiment;
    private final File projectDir;
    private final BuildExperimentRunner.Phase phase;
    private final int iterationNumber;
    private final int iterationMax;

    public DefaultBuildExperimentInvocationInfo(BuildExperimentSpec experiment, File projectDir, BuildExperimentRunner.Phase phase, int iterationNumber, int iterationMax) {
        this.experiment = experiment;
        this.projectDir = projectDir;
        this.phase = phase;
        this.iterationNumber = iterationNumber;
        this.iterationMax = iterationMax;
    }

    @Override
    public BuildExperimentSpec getBuildExperimentSpec() {
        return experiment;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public BuildExperimentRunner.Phase getPhase() {
        return phase;
    }

    @Override
    public int getIterationNumber() {
        return iterationNumber;
    }

    @Override
    public int getIterationMax() {
        return iterationMax;
    }
}
