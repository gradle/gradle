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

package org.gradle.performance.fixture;

import org.gradle.api.Action;
import org.gradle.performance.measure.MeasuredOperation;

import java.io.File;
import java.util.List;

public class BuildExperimentRunner {
    private final GCLoggingCollector gcCollector = new GCLoggingCollector();
    private final DataCollector dataCollector;
    private final GradleSessionProvider executerProvider;
    private final OperationTimer timer = new OperationTimer();

    public BuildExperimentRunner(GradleSessionProvider executerProvider) {
        this.executerProvider = executerProvider;
        MemoryInfoCollector memoryInfoCollector = new MemoryInfoCollector();
        memoryInfoCollector.setOutputFileName("build/totalMemoryUsed.txt");
        BuildEventTimestampCollector buildEventTimestampCollector = new BuildEventTimestampCollector("build/buildEventTimestamps.txt");
        dataCollector = new CompositeDataCollector(memoryInfoCollector, gcCollector, buildEventTimestampCollector);
    }

    public void run(BuildExperimentSpec experiment, MeasuredOperationList results) {
        System.out.println();
        System.out.println(String.format("%s ...", experiment.getDisplayName()));
        System.out.println();

        File workingDirectory = experiment.getInvocation().getWorkingDirectory();
        final List<String> additionalJvmOpts = dataCollector.getAdditionalJvmOpts(workingDirectory);
        final List<String> additionalArgs = dataCollector.getAdditionalArgs(workingDirectory);

        GradleInvocationSpec buildSpec = experiment.getInvocation().withAdditionalJvmOpts(additionalJvmOpts).withAdditionalArgs(additionalArgs);
        GradleSession session = executerProvider.session(buildSpec);

        session.prepare();
        try {
            for (int i = 0; i < experiment.getWarmUpCount(); i++) {
                System.out.println();
                System.out.println(String.format("Warm-up #%s", i + 1));
                runOnce(session, new MeasuredOperationList());
            }
            for (int i = 0; i < experiment.getInvocationCount(); i++) {
                System.out.println();
                System.out.println(String.format("Test run #%s", i + 1));
                runOnce(session, results);
            }
        } finally {
            session.cleanup();
        }
    }

    private void runOnce(final GradleSession session, MeasuredOperationList results) {
        final Runnable runner = session.runner();

        MeasuredOperation operation = timer.measure(new Action<MeasuredOperation>() {
            @Override
            public void execute(MeasuredOperation measuredOperation) {
                runner.run();
            }
        });

        if (operation.getException() == null) {
            dataCollector.collect(session.getInvocation().getWorkingDirectory(), operation);
        }

        results.add(operation);
    }
}
