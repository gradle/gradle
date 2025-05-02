/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.StartParameter;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.api.internal.tasks.userinput.BuildScanUserInputHandler;
import org.gradle.api.internal.tasks.userinput.DefaultBuildScanUserInputHandler;
import org.gradle.api.internal.tasks.userinput.DefaultUserInputHandler;
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.execution.WorkValidationWarningReporter;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.buildevents.BuildLoggerFactory;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeActionExecutor;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.InitDeprecationLoggingActionExecutor;
import org.gradle.internal.buildtree.InitProblems;
import org.gradle.internal.buildtree.ProblemReportingBuildActionRunner;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exception.ExceptionAnalyser;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner;
import org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner;
import org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor;
import org.gradle.launcher.exec.ChainingBuildActionRunner;
import org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor;
import org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor;
import org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor;
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory;
import org.gradle.problems.buildtree.ProblemReporter;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor;

import java.util.List;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class LauncherServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(BuildActionRunner.class, ExecuteBuildActionRunner.class);
        registration.addProvider(new ToolingGlobalScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildSessionScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildTreeScopeServices());
    }

    static class ToolingGlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        BuildLoggerFactory createBuildLoggerFactory(StyledTextOutputFactory styledTextOutputFactory, WorkValidationWarningReporter workValidationWarningReporter) {
            return new BuildLoggerFactory(styledTextOutputFactory, workValidationWarningReporter, Time.clock(), null);
        }
    }

    static class ToolingBuildSessionScopeServices implements ServiceRegistrationProvider {
        @Provides
        BuildSessionActionExecutor createActionExecutor(
            BuildEventListenerFactory listenerFactory,
            ExecutorFactory executorFactory,
            ListenerManager listenerManager,
            BuildOperationListenerManager buildOperationListenerManager,
            BuildOperationRunner buildOperationRunner,
            WorkInputListeners workListeners,
            FileChangeListeners fileChangeListeners,
            StyledTextOutputFactory styledTextOutputFactory,
            BuildRequestMetaData requestMetaData,
            BuildCancellationToken cancellationToken,
            DeploymentRegistryInternal deploymentRegistry,
            BuildEventConsumer eventConsumer,
            BuildStartedTime buildStartedTime,
            Clock clock,
            LoggingBuildOperationProgressBroadcaster loggingBuildOperationProgressBroadcaster,
            BuildOperationNotificationValve buildOperationNotificationValve,
            BuildTreeModelControllerServices buildModelServices,
            WorkerLeaseService workerLeaseService,
            BuildLayoutValidator buildLayoutValidator,
            FileSystem fileSystem,
            BuildLifecycleAwareVirtualFileSystem virtualFileSystem,
            ValueSnapshotter valueSnapshotter,
            ExceptionProblemRegistry problemContainer
        ) {
            CaseSensitivity caseSensitivity = fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE;
            return new SubscribableBuildActionExecutor(
                listenerManager,
                buildOperationListenerManager,
                listenerFactory, eventConsumer,
                new ContinuousBuildActionExecutor(
                    workListeners,
                    fileChangeListeners,
                    styledTextOutputFactory,
                    executorFactory,
                    requestMetaData,
                    cancellationToken,
                    deploymentRegistry,
                    listenerManager,
                    buildStartedTime,
                    clock,
                    fileSystem,
                    caseSensitivity,
                    virtualFileSystem,
                    new RunAsWorkerThreadBuildActionExecutor(
                        workerLeaseService,
                        new RunAsBuildOperationBuildActionExecutor(
                            new BuildTreeLifecycleBuildActionExecutor(buildModelServices, buildLayoutValidator, valueSnapshotter),
                            buildOperationRunner,
                            loggingBuildOperationProgressBroadcaster,
                            buildOperationNotificationValve,
                            problemContainer))));
        }

        @Provides
        UserInputHandler createUserInputHandler(BuildRequestMetaData requestMetaData, OutputEventListenerManager outputEventListenerManager, Clock clock, UserInputReader inputReader) {
            if (!requestMetaData.isInteractive()) {
                return new NonInteractiveUserInputHandler();
            }

            return new DefaultUserInputHandler(outputEventListenerManager.getBroadcaster(), clock, inputReader);
        }

        @Provides
        BuildScanUserInputHandler createBuildScanUserInputHandler(UserInputHandler userInputHandler) {
            return new DefaultBuildScanUserInputHandler(userInputHandler);
        }

    }

    static class ToolingBuildTreeScopeServices implements ServiceRegistrationProvider {
        @Provides
        ProblemStream createProblemStream(StartParameter parameter, ProblemDiagnosticsFactory diagnosticsFactory) {
            return parameter.getWarningMode().shouldDisplayMessages() ? diagnosticsFactory.newUnlimitedStream() : diagnosticsFactory.newStream();
        }

        @Provides
        BuildTreeActionExecutor createActionExecutor(
            List<BuildActionRunner> buildActionRunners,
            StyledTextOutputFactory styledTextOutputFactory,
            BuildStateRegistry buildStateRegistry,
            BuildOperationProgressEventEmitter eventEmitter,
            ListenerManager listenerManager,
            BuildStartedTime buildStartedTime,
            BuildRequestMetaData buildRequestMetaData,
            GradleEnterprisePluginManager gradleEnterprisePluginManager,
            BuildLifecycleAwareVirtualFileSystem virtualFileSystem,
            DeploymentRegistryInternal deploymentRegistry,
            StatStatistics.Collector statStatisticsCollector,
            FileHasherStatistics.Collector fileHasherStatisticsCollector,
            DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector,
            BuildOperationRunner buildOperationRunner,
            BuildLayout buildLayout,
            ExceptionAnalyser exceptionAnalyser,
            List<ProblemReporter> problemReporters,
            BuildLoggerFactory buildLoggerFactory,
            InternalOptions options,
            StartParameter startParameter,
            InternalProblems problemsService,
            ProblemStream problemStream,
            ExceptionProblemRegistry registry
        ) {
            return new InitProblems(
                new InitDeprecationLoggingActionExecutor(
                    new RootBuildLifecycleBuildActionExecutor(
                        buildStateRegistry,
                        new BuildCompletionNotifyingBuildActionRunner(
                            new FileSystemWatchingBuildActionRunner(
                                eventEmitter,
                                virtualFileSystem,
                                deploymentRegistry,
                                statStatisticsCollector,
                                fileHasherStatisticsCollector,
                                directorySnapshotterStatisticsCollector,
                                buildOperationRunner,
                                new BuildOutcomeReportingBuildActionRunner(
                                    styledTextOutputFactory,
                                    listenerManager,
                                    new ProblemReportingBuildActionRunner(
                                        new ChainingBuildActionRunner(buildActionRunners),
                                        exceptionAnalyser,
                                        buildLayout,
                                        problemReporters
                                    ),
                                    buildStartedTime,
                                    buildRequestMetaData,
                                    buildLoggerFactory,
                                    registry
                                ),
                                options),
                            gradleEnterprisePluginManager)),
                    eventEmitter,
                    startParameter,
                    problemsService,
                    problemStream),
                problemsService);
        }

        @Provides
        BuildLoggerFactory createBuildLoggerFactory(StyledTextOutputFactory styledTextOutputFactory, WorkValidationWarningReporter workValidationWarningReporter, Clock clock, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
            return new BuildLoggerFactory(styledTextOutputFactory, workValidationWarningReporter, clock, gradleEnterprisePluginManager);
        }
    }
}
