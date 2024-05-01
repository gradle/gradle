/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildInstantReplay;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.InstantReplaySomething;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Actually executes the build.
 *
 * Typically the last action in the pipeline.
 */
public class ExecuteBuild extends BuildCommandOnly {

    private static final Logger LOGGER = Logging.getLogger(ExecuteBuild.class);

    final private BuildActionExecuter<BuildActionParameters, BuildRequestContext> actionExecuter;
    private final DaemonRunningStats runningStats;

    public ExecuteBuild(BuildActionExecuter<BuildActionParameters, BuildRequestContext> actionExecuter, DaemonRunningStats runningStats) {
        this.actionExecuter = actionExecuter;
        this.runningStats = runningStats;
    }

    @Override
    protected void doBuild(final DaemonCommandExecution execution, Build build) {
        LOGGER.debug(DaemonMessages.STARTED_BUILD);
        LOGGER.debug("Executing build with daemon context: {}", execution.getDaemonContext());
        runningStats.buildStarted();
        DaemonConnectionBackedEventConsumer buildEventConsumer = new DaemonConnectionBackedEventConsumer(execution);
        try {
            BuildCancellationToken cancellationToken = execution.getDaemonStateControl().getCancellationToken();
            BuildRequestContext buildRequestContext = new DefaultBuildRequestContext(build.getBuildRequestMetaData(), cancellationToken, buildEventConsumer);

            String buildScan = build.getAction().getStartParameter().getSystemPropertiesArgs().get("oAgain");
            if (buildScan != null) {
                URI url = URI.create(buildScan);
                BuildInstantReplay instantReplay = new InstantReplaySomething().retrieve(url, build.getAction().getStartParameter().getGradleUserHomeDir());

                if (instantReplay.getTestFailures().isEmpty()) {
                    build.getAction().getStartParameter().setTaskNames(instantReplay.getRequestedTaskSelectors());
                } else {

                    List<String> taskNames = instantReplay.getTestFailures().stream().collect(groupingBy(BuildInstantReplay.TestFailure::getTaskPath)).entrySet().stream().flatMap(entry -> {
                        List<String> args = new ArrayList<String>();
                        args.add(entry.getKey());
                        for (BuildInstantReplay.TestFailure failure : entry.getValue()) {
                            args.add("--tests");
                            args.add(failure.getFailedTest());
                        }
                        return args.stream();
                    }).collect(Collectors.toList());
                    System.out.println("taskNames: " + taskNames);
                    build.getAction().getStartParameter().setTaskNames(taskNames);
                }
            }

            if (!build.getAction().getStartParameter().isContinuous()) {
                buildRequestContext.getCancellationToken().addCallback(new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.info(DaemonMessages.CANCELED_BUILD);
                    }
                });
            }
            BuildActionResult result = actionExecuter.execute(build.getAction(), build.getParameters(), buildRequestContext);
            execution.setResult(result);
        } finally {
            buildEventConsumer.waitForFinish();
            runningStats.buildFinished();
            LOGGER.debug(DaemonMessages.FINISHED_BUILD);
        }

        execution.proceed(); // ExecuteBuild should be the last action, but in case we want to decorate the result in the future
    }

}
