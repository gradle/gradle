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

import com.google.common.collect.Lists;
import org.gradle.StartParameter;
import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsEventAdapter;
import org.gradle.api.logging.Logging;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.buildevents.BuildLogger;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildevents.TaskExecutionStatisticsReporter;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;

import java.util.List;

public class BuildOutcomeReportingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final StyledTextOutputFactory styledTextOutputFactory;

    public BuildOutcomeReportingBuildActionRunner(StyledTextOutputFactory styledTextOutputFactory, BuildActionRunner delegate) {
        this.styledTextOutputFactory = styledTextOutputFactory;
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildController buildController) {
        StartParameter startParameter = buildController.getGradle().getStartParameter();
        ServiceRegistry services = buildController.getGradle().getServices();
        BuildStartedTime buildStartedTime = services.get(BuildStartedTime.class);
        BuildRequestMetaData buildRequestMetaData = services.get(BuildRequestMetaData.class);
        Clock clock = services.get(Clock.class);
        ListenerManager listenerManager = services.get(ListenerManager.class);
        TaskExecutionStatisticsEventAdapter taskStatisticsCollector = new TaskExecutionStatisticsEventAdapter();
        listenerManager.addListener(taskStatisticsCollector);

        BuildLogger buildLogger = new BuildLogger(Logging.getLogger(BuildLogger.class), styledTextOutputFactory, startParameter, buildRequestMetaData, buildStartedTime, clock);
        // Register as a 'logger' to support this being replaced by build logic.
        buildController.getGradle().useLogger(buildLogger);

        Result result = delegate.run(action, buildController);

        Throwable failure = DeprecationLogger.getDeprecationFailure();
        if (failure != null) {
            // Replace result if we fail on warning
            result = computeUpdatedResult(result, failure);
        }

        buildLogger.logResult(result.getBuildFailure());
        new TaskExecutionStatisticsReporter(styledTextOutputFactory).buildFinished(taskStatisticsCollector.getStatistics());
        return result;
    }

    private Result computeUpdatedResult(Result previousResult, Throwable deprecationFailure) {
        if (previousResult.getBuildFailure() != null) {
            // Enhance already reported failures
            Throwable buildFailure = previousResult.getBuildFailure();
            List<Throwable> newFailures;
            if (buildFailure instanceof MultipleBuildFailures) {
                MultipleBuildFailures multipleBuildFailures = (MultipleBuildFailures) buildFailure;
                newFailures = Lists.newArrayListWithExpectedSize(multipleBuildFailures.getCauses().size() + 1);
                newFailures.addAll(multipleBuildFailures.getCauses());
            } else {
                newFailures = Lists.newArrayListWithExpectedSize(2);
                newFailures.add(buildFailure);
            }
            newFailures.add(deprecationFailure);
            previousResult = Result.failed(new MultipleBuildFailures(newFailures));
        } else {
            previousResult = Result.failed(deprecationFailure);
        }
        return previousResult;
    }
}
