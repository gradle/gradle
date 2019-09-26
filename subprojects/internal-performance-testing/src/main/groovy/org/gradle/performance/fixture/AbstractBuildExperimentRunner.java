/*
 * Copyright 2019 the original author or authors.
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

import org.apache.commons.io.FileUtils;
import org.gradle.performance.results.MeasuredOperationList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Runs a single build experiment.
 *
 * As part of a performance scenario, multiple experiments need to be run and compared.
 * For example for a cross-version scenario, experiments for each version will be run.
 */
public abstract class AbstractBuildExperimentRunner implements BuildExperimentRunner {

    @Override
    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        InvocationSpec invocationSpec = experiment.getInvocation();
        File workingDirectory = invocationSpec.getWorkingDirectory();
        workingDirectory.mkdirs();
        copyTemplateTo(experiment, workingDirectory);

        doRun(experiment, results);
    }

    protected abstract void doRun(BuildExperimentSpec experiment, MeasuredOperationList results);

    private static void copyTemplateTo(BuildExperimentSpec experiment, File workingDir) {
        try {
            File templateDir = new TestProjectLocator().findProjectDir(experiment.getProjectName());
            FileUtils.cleanDirectory(workingDir);
            FileUtils.copyDirectory(templateDir, workingDir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String getExperimentOverride(String key) {
        String value = System.getProperty("org.gradle.performance.execution." + key);
        if (value != null && !"defaults".equals(value)) {
            return value;
        }
        return null;
    }

    protected static Integer invocationsForExperiment(BuildExperimentSpec experiment) {
        String overriddenInvocationCount = getExperimentOverride("runs");
        if (overriddenInvocationCount != null) {
            return Integer.valueOf(overriddenInvocationCount);
        }
        if (experiment.getInvocationCount() != null) {
            return experiment.getInvocationCount();
        }
        return 40;
    }

    protected static int warmupsForExperiment(BuildExperimentSpec experiment) {
        String overriddenWarmUpCount = getExperimentOverride("warmups");
        if (overriddenWarmUpCount != null) {
            return Integer.parseInt(overriddenWarmUpCount);
        }
        if (experiment.getWarmUpCount() != null) {
            return experiment.getWarmUpCount();
        }
        if (usesDaemon(experiment)) {
            return 10;
        } else {
            return 1;
        }
    }

    private static boolean usesDaemon(BuildExperimentSpec experiment) {
        InvocationSpec invocation = experiment.getInvocation();
        if (invocation instanceof GradleInvocationSpec) {
            return ((GradleInvocationSpec) invocation).getBuildWillRunInDaemon();
        }
        return false;
    }
}
