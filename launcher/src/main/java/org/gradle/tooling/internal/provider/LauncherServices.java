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

import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.execution.WorkValidationWarningReporter;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.buildevents.BuildLoggerFactory;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeActionExecutor;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.ProblemReportingBuildActionRunner;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.logging.LoggingBuildOperationProgressBroadcaster;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.internal.watch.vfs.FileSystemWatchingInformation;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner;
import org.gradle.launcher.exec.BuildTreeLifecycleBuildActionExecutor;
import org.gradle.launcher.exec.ChainingBuildActionRunner;
import org.gradle.launcher.exec.RootBuildLifecycleBuildActionExecutor;
import org.gradle.launcher.exec.RunAsBuildOperationBuildActionExecutor;
import org.gradle.launcher.exec.RunAsWorkerThreadBuildActionExecutor;
import org.gradle.problems.buildtree.ProblemReporter;
import org.gradle.tooling.internal.provider.continuous.ContinuousBuildActionExecutor;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DaemonSidePayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

import java.util.List;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class LauncherServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingGlobalScopeServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingGradleUserHomeScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildSessionScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new ToolingBuildTreeScopeServices());
    }

    static class ToolingGlobalScopeServices {
        BuildExecuter createBuildExecuter(
            LoggingManagerInternal loggingManager,
            BuildLoggerFactory buildLoggerFactory,
            GradleUserHomeScopeServiceRegistry userHomeServiceRegistry,
            ServiceRegistry globalServices
        ) {
            // @formatter:off
            return
                new SetupLoggingActionExecuter(loggingManager,
                new SessionFailureReportingActionExecuter(buildLoggerFactory,
                new StartParamsValidatingActionExecuter(
                new BuildSessionLifecycleBuildActionExecuter(userHomeServiceRegistry, globalServices
                ))));
            // @formatter:on
        }

        BuildLoggerFactory createBuildLoggerFactory(StyledTextOutputFactory styledTextOutputFactory, WorkValidationWarningReporter workValidationWarningReporter) {
            return new BuildLoggerFactory(styledTextOutputFactory, workValidationWarningReporter, Time.clock(), null);
        }

        ExecuteBuildActionRunner createExecuteBuildActionRunner() {
            return new ExecuteBuildActionRunner();
        }

        ClassLoaderCache createClassLoaderCache() {
            return new ClassLoaderCache();
        }
    }

    static class ToolingGradleUserHomeScopeServices {
        PayloadClassLoaderFactory createClassLoaderFactory(CachedClasspathTransformer cachedClasspathTransformer) {
            return new DaemonSidePayloadClassLoaderFactory(
                new ModelClassLoaderFactory(),
                cachedClasspathTransformer);
        }

        PayloadSerializer createPayloadSerializer(ClassLoaderCache classLoaderCache, PayloadClassLoaderFactory classLoaderFactory) {
            return new PayloadSerializer(
                new WellKnownClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        classLoaderFactory))
            );
        }
    }

    static class ToolingBuildSessionScopeServices {
        BuildSessionActionExecutor createActionExecutor(
            BuildEventListenerFactory listenerFactory,
            ExecutorFactory executorFactory,
            ListenerManager listenerManager,
            BuildOperationListenerManager buildOperationListenerManager,
            BuildOperationExecutor buildOperationExecutor,
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
            FileSystemWatchingInformation fileSystemWatchingInformation
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
                    fileSystemWatchingInformation,
                    new RunAsWorkerThreadBuildActionExecutor(
                        workerLeaseService,
                        new RunAsBuildOperationBuildActionExecutor(
                            new BuildTreeLifecycleBuildActionExecutor(buildModelServices, buildLayoutValidator),
                            buildOperationExecutor,
                            loggingBuildOperationProgressBroadcaster,
                            buildOperationNotificationValve))));
        }
    }

    static class ToolingBuildTreeScopeServices {
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
            BuildLoggerFactory buildLoggerFactory
        ) {
            return new RootBuildLifecycleBuildActionExecutor(
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
                            buildLoggerFactory)),
                    gradleEnterprisePluginManager));
        }

        BuildLoggerFactory createBuildLoggerFactory(StyledTextOutputFactory styledTextOutputFactory, WorkValidationWarningReporter workValidationWarningReporter, Clock clock, GradleEnterprisePluginManager gradleEnterprisePluginManager) {
            return new BuildLoggerFactory(styledTextOutputFactory, workValidationWarningReporter, clock, gradleEnterprisePluginManager);
        }
    }
}
