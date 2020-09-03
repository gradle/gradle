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

import org.gradle.api.execution.internal.TaskInputsListeners;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileSystemChangeWaiterFactory;
import org.gradle.internal.filewatch.FileSystemChangeWaiterFactory;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.launcher.exec.BuildCompletionNotifyingBuildActionRunner;
import org.gradle.launcher.exec.BuildExecuter;
import org.gradle.launcher.exec.BuildOutcomeReportingBuildActionRunner;
import org.gradle.launcher.exec.BuildTreeBuildActionExecutor;
import org.gradle.launcher.exec.BuildTreeScopeLifecycleBuildActionExecuter;
import org.gradle.launcher.exec.ChainingBuildActionRunner;
import org.gradle.launcher.exec.InProcessBuildActionExecuter;
import org.gradle.launcher.exec.RunAsBuildOperationBuildActionRunner;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DaemonSidePayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

import java.util.List;

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
        BuildExecuter createBuildExecuter(StyledTextOutputFactory styledTextOutputFactory,
                                          LoggingManagerInternal loggingManager,
                                          GradleUserHomeScopeServiceRegistry userHomeServiceRegistry,
                                          ServiceRegistry globalServices) {
            // @formatter:off
            return
                new SetupLoggingActionExecuter(loggingManager,
                new SessionFailureReportingActionExecuter(styledTextOutputFactory, Time.clock(),
                new StartParamsValidatingActionExecuter(
                new GradleThreadBuildActionExecuter(
                new SessionScopeLifecycleBuildActionExecuter(userHomeServiceRegistry, globalServices
                )))));
            // @formatter:on
        }

        FileSystemChangeWaiterFactory createFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory) {
            return new DefaultFileSystemChangeWaiterFactory(fileWatcherFactory);
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
        SessionScopeBuildActionExecutor createActionExecutor(BuildEventListenerFactory listenerFactory,
                                                             ExecutorFactory executorFactory,
                                                             ListenerManager listenerManager,
                                                             BuildOperationListenerManager buildOperationListenerManager,
                                                             TaskInputsListeners inputsListeners,
                                                             StyledTextOutputFactory styledTextOutputFactory,
                                                             FileSystemChangeWaiterFactory fileSystemChangeWaiterFactory,
                                                             BuildRequestMetaData requestMetaData,
                                                             BuildCancellationToken cancellationToken,
                                                             DeploymentRegistryInternal deploymentRegistry,
                                                             BuildEventConsumer eventConsumer,
                                                             BuildStartedTime buildStartedTime,
                                                             Clock clock
        ) {
            return new SubscribableBuildActionExecuter(listenerManager, buildOperationListenerManager, listenerFactory, eventConsumer,
                new ContinuousBuildActionExecuter(fileSystemChangeWaiterFactory, inputsListeners, styledTextOutputFactory, executorFactory, requestMetaData, cancellationToken, deploymentRegistry, listenerManager, buildStartedTime, clock,
                    new BuildTreeScopeLifecycleBuildActionExecuter()));
        }
    }

    static class ToolingBuildTreeScopeServices {
        BuildTreeBuildActionExecutor createActionExecuter(List<BuildActionRunner> buildActionRunners,
                                                          StyledTextOutputFactory styledTextOutputFactory,
                                                          BuildStateRegistry buildStateRegistry,
                                                          PayloadSerializer payloadSerializer,
                                                          BuildOperationNotificationValve buildOperationNotificationValve,
                                                          BuildCancellationToken buildCancellationToken
        ) {
            return new InProcessBuildActionExecuter(buildStateRegistry, payloadSerializer, buildOperationNotificationValve, buildCancellationToken,
                new RunAsBuildOperationBuildActionRunner(
                    new BuildCompletionNotifyingBuildActionRunner(
                        new FileSystemWatchingBuildActionRunner(
                            new ValidatingBuildActionRunner(
                                new BuildOutcomeReportingBuildActionRunner(styledTextOutputFactory,
                                    new ChainingBuildActionRunner(buildActionRunners)))))));
        }
    }
}
