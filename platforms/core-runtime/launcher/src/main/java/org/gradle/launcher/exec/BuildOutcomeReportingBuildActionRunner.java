/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.StartParameter;
import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsEventAdapter;
import org.gradle.api.logging.Logging;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.buildevents.BuildLogger;
import org.gradle.internal.buildevents.BuildLoggerFactory;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildevents.TaskExecutionStatisticsReporter;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class BuildOutcomeReportingBuildActionRunner implements BuildActionRunner {
    private final ListenerManager listenerManager;
    private final BuildActionRunner delegate;
    private final BuildStartedTime buildStartedTime;
    private final BuildRequestMetaData buildRequestMetaData;
    private final StyledTextOutputFactory styledTextOutputFactory;
    private final BuildLoggerFactory buildLoggerFactory;
    private final InternalProblems problemsService;

    public BuildOutcomeReportingBuildActionRunner(StyledTextOutputFactory styledTextOutputFactory,
                                                  ListenerManager listenerManager,
                                                  BuildActionRunner delegate,
                                                  BuildStartedTime buildStartedTime,
                                                  BuildRequestMetaData buildRequestMetaData,
                                                  BuildLoggerFactory buildLoggerFactory,
                                                  InternalProblems problemsService
    ) {
        this.styledTextOutputFactory = styledTextOutputFactory;
        this.listenerManager = listenerManager;
        this.delegate = delegate;
        this.buildStartedTime = buildStartedTime;
        this.buildRequestMetaData = buildRequestMetaData;
        this.buildLoggerFactory = buildLoggerFactory;
        this.problemsService = problemsService;
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        StartParameter startParameter = action.getStartParameter();
        TaskExecutionStatisticsEventAdapter taskStatisticsCollector = new TaskExecutionStatisticsEventAdapter();
        listenerManager.addListener(taskStatisticsCollector);

        BuildLogger buildLogger = buildLoggerFactory.create(Logging.getLogger(BuildLogger.class), startParameter, buildStartedTime, buildRequestMetaData);
        // Register as a 'logger' to support this being replaced by build logic.
        buildController.beforeBuild(gradle -> gradle.useLogger(buildLogger));

        Result result = delegate.run(action, buildController);

        buildLogger.logResult(result.getBuildFailure());
        new TaskExecutionStatisticsReporter(styledTextOutputFactory).buildFinished(taskStatisticsCollector.getStatistics());
        problemsService.reportMapping();
        return result;
    }
}
