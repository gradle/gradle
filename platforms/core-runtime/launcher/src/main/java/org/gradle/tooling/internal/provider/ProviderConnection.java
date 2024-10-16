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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.userinput.UserInputReader;
import org.gradle.api.logging.LogLevel;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.NotifyDaemonAboutChangedPathsClient;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonRequestContext;
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria;
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.process.internal.streams.SafeStreams;
import org.gradle.tooling.events.OperationType;
import org.gradle.tooling.internal.build.DefaultBuildEnvironment;
import org.gradle.tooling.internal.consumer.parameters.FailsafeBuildProgressListenerAdapter;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction;
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction;
import org.gradle.tooling.internal.provider.connection.ProviderConnectionParameters;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

@ServiceScope(Scope.Global.class)
public class ProviderConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderConnection.class);
    private final PayloadSerializer payloadSerializer;
    private final BuildLayoutFactory buildLayoutFactory;
    private final DaemonClientFactory daemonClientFactory;
    private final BuildActionExecutor<BuildActionParameters, BuildRequestContext> embeddedExecutor;
    private final ServiceRegistry sharedServices;
    private final FileCollectionFactory fileCollectionFactory;
    private final GlobalUserInputReceiver userInputReceiver;
    private final UserInputReader userInputReader;

    private GradleVersion consumerVersion;

    public ProviderConnection(
        ServiceRegistry sharedServices,
        BuildLayoutFactory buildLayoutFactory,
        DaemonClientFactory daemonClientFactory,
        BuildActionExecutor<BuildActionParameters, BuildRequestContext> embeddedExecutor,
        PayloadSerializer payloadSerializer,
        FileCollectionFactory fileCollectionFactory,
        GlobalUserInputReceiver userInputReceiver,
        UserInputReader userInputReader
    ) {
        this.buildLayoutFactory = buildLayoutFactory;
        this.daemonClientFactory = daemonClientFactory;
        this.embeddedExecutor = embeddedExecutor;
        this.payloadSerializer = payloadSerializer;
        this.sharedServices = sharedServices;
        this.fileCollectionFactory = fileCollectionFactory;
        this.userInputReceiver = userInputReceiver;
        this.userInputReader = userInputReader;
    }

    public void configure(ProviderConnectionParameters parameters, GradleVersion consumerVersion) {
        this.consumerVersion = consumerVersion;
        LogLevel providerLogLevel = parameters.getVerboseLogging() ? LogLevel.DEBUG : LogLevel.INFO;
        LOGGER.debug("Configuring logging to level: {}", providerLogLevel);
        LoggingManagerInternal loggingManager = sharedServices.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevelInternal(providerLogLevel);
        loggingManager.start();
    }

    public Object run(String modelName, BuildCancellationToken cancellationToken, ProviderOperationParameters providerParameters) {
        List<String> tasks = providerParameters.getTasks();
        if (modelName.equals(ModelIdentifier.NULL_MODEL) && tasks == null) {
            throw new IllegalArgumentException("No model type or tasks specified.");
        }
        Parameters params = initParams(providerParameters);
        if (BuildEnvironment.class.getName().equals(modelName)) {
            //we don't really need to launch the daemon to acquire information needed for BuildEnvironment
            if (tasks != null) {
                throw new IllegalArgumentException("Cannot run tasks and fetch the build environment model.");
            }
            return new DefaultBuildEnvironment(
                new DefaultBuildIdentifier(providerParameters.getProjectDir()),
                params.buildLayout.getGradleUserHomeDir(),
                GradleVersion.current().getVersion(),
                reportableJavaHomeForBuild(params),
                params.daemonParams.getEffectiveJvmArgs());
        }

        ProgressListenerConfiguration listenerConfig = ProgressListenerConfiguration.from(providerParameters, consumerVersion, payloadSerializer);
        BuildAction action = new BuildModelAction(params.startParameter, modelName, tasks != null, listenerConfig.clientSubscriptions);
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params);
    }

    private static File reportableJavaHomeForBuild(Parameters params) {
        DaemonParameters daemonParameters = params.daemonParams;
        DaemonJvmCriteria criteria = daemonParameters.getRequestedJvmCriteria();
        if (criteria instanceof DaemonJvmCriteria.Spec) {
            // Gradle daemon properties have been defined
            // TODO: We don't know what this will be without searching.
            // We'll say it's the current JVM because we don't know any better for now.
            return Jvm.current().getJavaHome();
        } else if (criteria instanceof DaemonJvmCriteria.JavaHome) {
            return ((DaemonJvmCriteria.JavaHome) criteria).getJavaHome();
        } else if (criteria instanceof DaemonJvmCriteria.LauncherJvm) {
            return Jvm.current().getJavaHome();
        } else {
            throw new IllegalStateException("Unknown DaemonJvmCriteria type: " + criteria.getClass().getName());
        }
    }

    @SuppressWarnings({"deprecation", "overloads"})
    public Object run(org.gradle.tooling.internal.protocol.InternalBuildAction<?> clientAction, BuildCancellationToken cancellationToken, ProviderOperationParameters providerParameters) {
        return runClientAction(clientAction, cancellationToken, providerParameters);
    }

    @SuppressWarnings("overloads")
    public Object run(InternalBuildActionVersion2<?> clientAction, BuildCancellationToken cancellationToken, ProviderOperationParameters providerParameters) {
        return runClientAction(clientAction, cancellationToken, providerParameters);
    }

    public Object runClientAction(Object clientAction, BuildCancellationToken cancellationToken, ProviderOperationParameters providerParameters) {
        List<String> tasks = providerParameters.getTasks();
        SerializedPayload serializedAction = payloadSerializer.serialize(clientAction);
        Parameters params = initParams(providerParameters);
        StartParameterInternal startParameter = new ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties);
        ProgressListenerConfiguration listenerConfig = ProgressListenerConfiguration.from(providerParameters, consumerVersion, payloadSerializer);
        BuildAction action = new ClientProvidedBuildAction(startParameter, serializedAction, tasks != null, listenerConfig.clientSubscriptions);
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params);
    }

    public Object runPhasedAction(
        InternalPhasedAction clientPhasedAction,
        PhasedActionResultListener resultListener,
        BuildCancellationToken cancellationToken,
        ProviderOperationParameters providerParameters
    ) {
        List<String> tasks = providerParameters.getTasks();
        SerializedPayload serializedAction = payloadSerializer.serialize(clientPhasedAction);
        Parameters params = initParams(providerParameters);
        StartParameterInternal startParameter = new ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties);
        FailsafePhasedActionResultListener failsafePhasedActionResultListener = new FailsafePhasedActionResultListener(resultListener);
        ProgressListenerConfiguration listenerConfig = ProgressListenerConfiguration.from(providerParameters, consumerVersion, payloadSerializer);
        BuildAction action = new ClientProvidedPhasedAction(startParameter, serializedAction, tasks != null, listenerConfig.clientSubscriptions);
        try {
            return run(action, cancellationToken, listenerConfig, new PhasedActionEventConsumer(failsafePhasedActionResultListener, payloadSerializer, listenerConfig.buildEventConsumer),
                providerParameters, params);
        } finally {
            failsafePhasedActionResultListener.rethrowErrors();
        }
    }

    public Object runTests(ProviderInternalTestExecutionRequest testExecutionRequest, BuildCancellationToken cancellationToken, ProviderOperationParameters providerParameters) {
        Parameters params = initParams(providerParameters);
        StartParameterInternal startParameter = new ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties);
        ProgressListenerConfiguration listenerConfig = ProgressListenerConfiguration.from(providerParameters, consumerVersion, payloadSerializer);
        TestExecutionRequestAction action = TestExecutionRequestAction.create(listenerConfig.clientSubscriptions, startParameter, testExecutionRequest);
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params);
    }

    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, ProviderOperationParameters providerParameters) {
        ServiceRegistry requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging();
        Parameters params = initParams(providerParameters);
        ServiceRegistry clientServices = daemonClientFactory.createMessageDaemonServices(requestSpecificLoggingServices, params.daemonParams);
        NotifyDaemonAboutChangedPathsClient client = clientServices.get(NotifyDaemonAboutChangedPathsClient.class);
        client.notifyDaemonsAboutChangedPaths(changedPaths);
    }

    public void stopWhenIdle(ProviderOperationParameters providerParameters) {
        ServiceRegistry requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging();
        Parameters params = initParams(providerParameters);
        ServiceRegistry clientServices = daemonClientFactory.createMessageDaemonServices(requestSpecificLoggingServices, params.daemonParams);
        ((ShutdownCoordinator) clientServices.find(ShutdownCoordinator.class)).stop();
    }

    private Object run(
        BuildAction action, BuildCancellationToken cancellationToken,
        ProgressListenerConfiguration progressListenerConfiguration,
        BuildEventConsumer buildEventConsumer,
        ProviderOperationParameters providerParameters,
        Parameters parameters
    ) {
        try {
            BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext> executor = createExecutor(providerParameters, parameters);
            boolean interactive = providerParameters.getStandardInput() != null;
            ClientBuildRequestContext context = new ClientBuildRequestContext(new GradleLauncherMetaData(), providerParameters.getStartTime(), interactive, cancellationToken, buildEventConsumer);
            BuildActionResult result = executor.execute(action, new ConnectionOperationParameters(parameters.daemonParams, parameters.tapiSystemProperties, providerParameters), context);
            throwFailure(result);
            return payloadSerializer.deserialize(result.getResult());
        } finally {
            progressListenerConfiguration.failsafeWrapper.rethrowErrors();
        }
    }

    private void throwFailure(BuildActionResult result) {
        if (result.getException() != null) {
            throw map(result, result.getException());
        }
        if (result.getFailure() != null) {
            throw map(result, (RuntimeException) payloadSerializer.deserialize(result.getFailure()));
        }
    }

    private RuntimeException map(BuildActionResult result, RuntimeException exception) {
        // Wrap build failure in 'cancelled' cross version exception
        if (result.wasCancelled()) {
            throw new InternalBuildCancelledException(exception);
        }

        // Forward special cases directly to consumer
        if (exception instanceof InternalTestExecutionException || exception instanceof InternalBuildActionFailureException || exception instanceof InternalUnsupportedModelException) {
            return exception;
        }

        // Wrap in generic 'build failed' cross version exception
        throw new BuildExceptionVersion1(exception);
    }

    private BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext> createExecutor(ProviderOperationParameters operationParameters, Parameters params) {
        LoggingManagerInternal loggingManager;
        BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext> executor;
        InputStream standardInput = operationParameters.getStandardInput();
        if (standardInput == null) {
            standardInput = SafeStreams.emptyInput();
        }
        if (Boolean.TRUE.equals(operationParameters.isEmbedded())) {
            loggingManager = sharedServices.getFactory(LoggingManagerInternal.class).create();
            loggingManager.captureSystemSources();
            executor = new RunInProcess(new SystemPropertySetterExecuter(new ForwardStdInToThisProcess(userInputReceiver, userInputReader, standardInput, embeddedExecutor)));
        } else {
            ServiceRegistry requestSpecificLogging = LoggingServiceRegistry.newNestedLogging();
            loggingManager = requestSpecificLogging.getFactory(LoggingManagerInternal.class).create();
            ServiceRegistry clientServices = daemonClientFactory.createBuildClientServices(requestSpecificLogging, params.daemonParams, params.requestContext, standardInput);
            executor = clientServices.get(DaemonClient.class);
        }
        return new LoggingBridgingBuildActionExecuter(new DaemonBuildActionExecuter(executor), loggingManager);
    }

    private Parameters initParams(ProviderOperationParameters operationParameters) {
        CommandLineParser commandLineParser = new CommandLineParser();
        commandLineParser.allowUnknownOptions();
        commandLineParser.allowMixedSubcommandsAndOptions();

        InitialPropertiesConverter initialPropertiesConverter = new InitialPropertiesConverter();
        BuildLayoutConverter buildLayoutConverter = new BuildLayoutConverter();
        initialPropertiesConverter.configure(commandLineParser);
        buildLayoutConverter.configure(commandLineParser);

        ParsedCommandLine parsedCommandLine = commandLineParser.parse(operationParameters.getArguments() == null ? Collections.emptyList() : operationParameters.getArguments());

        InitialProperties initialProperties = initialPropertiesConverter.convert(parsedCommandLine);
        BuildLayoutResult buildLayoutResult = buildLayoutConverter.convert(initialProperties, parsedCommandLine, operationParameters.getProjectDir(), layout -> {
            if (operationParameters.getGradleUserHomeDir() != null) {
                layout.setGradleUserHomeDir(operationParameters.getGradleUserHomeDir());
            }
            layout.setProjectDir(operationParameters.getProjectDir());
        });

        AllProperties properties = new LayoutToPropertiesConverter(buildLayoutFactory).convert(initialProperties, buildLayoutResult);

        DaemonParameters daemonParams = new DaemonParameters(buildLayoutResult, fileCollectionFactory);
        new DaemonBuildOptions().propertiesConverter().convert(properties.getProperties(), daemonParams);
        if (operationParameters.getDaemonBaseDir() != null) {
            daemonParams.setBaseDir(operationParameters.getDaemonBaseDir());
        }

        //override the params with the explicit settings provided by the tooling api
        List<String> jvmArguments = operationParameters.getJvmArguments();
        if (jvmArguments != null) {
            daemonParams.setJvmArgs(jvmArguments);
        }

        daemonParams.setRequestedJvmCriteriaFromMap(properties.getDaemonJvmProperties());

        // Include the system properties that are defined in the daemon JVM args
        properties = properties.merge(daemonParams.getSystemProperties());

        Map<String, String> envVariables = operationParameters.getEnvironmentVariables(null);
        if (envVariables != null) {
            daemonParams.setEnvironmentVariables(envVariables);
        }

        File javaHome = operationParameters.getJavaHome();
        if (javaHome != null) {
            daemonParams.setRequestedJvmCriteria(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.TOOLING_API_CLIENT, javaHome));
        }

        DaemonRequestContext requestContext = daemonParams.toRequestContext();

        if (operationParameters.getDaemonMaxIdleTimeValue() != null && operationParameters.getDaemonMaxIdleTimeUnits() != null) {
            int idleTimeout = (int) operationParameters.getDaemonMaxIdleTimeUnits().toMillis(operationParameters.getDaemonMaxIdleTimeValue());
            daemonParams.setIdleTimeout(idleTimeout);
        }

        Map<String, String> effectiveSystemProperties = new HashMap<>();
        Map<String, String> operationParametersSystemProperties = operationParameters.getSystemProperties(null);
        if (operationParametersSystemProperties != null) {
            effectiveSystemProperties.putAll(operationParametersSystemProperties);
            effectiveSystemProperties.putAll(daemonParams.getMutableAndImmutableSystemProperties());
        } else {
            GUtil.addToMap(effectiveSystemProperties, System.getProperties());
            effectiveSystemProperties.putAll(daemonParams.getMutableAndImmutableSystemProperties());
        }
        StartParameterInternal startParameter = new ProviderStartParameterConverter().toStartParameter(operationParameters, buildLayoutResult, properties);

        Map<String, String> gradlePropertiesAsSeenByToolchains = new HashMap<>();
        gradlePropertiesAsSeenByToolchains.putAll(properties.getProperties());
        gradlePropertiesAsSeenByToolchains.putAll(startParameter.getProjectProperties());
        new BuildOptionBackedConverter<>(new ToolchainBuildOptions()).convert(parsedCommandLine, gradlePropertiesAsSeenByToolchains, daemonParams.getToolchainConfiguration());

        return new Parameters(daemonParams, buildLayoutResult, properties, effectiveSystemProperties, startParameter, requestContext);
    }

    public void ping(ProviderOperationParameters providerParameters) {
        ServiceRegistry requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging();
        Parameters params = initParams(providerParameters);
        ServiceRegistry clientServices = daemonClientFactory.createMessageDaemonServices(requestSpecificLoggingServices, params.daemonParams);
        ((PingCoordinator) clientServices.find(PingCoordinator.class)).ping();
    }

    private static class Parameters {
        final DaemonParameters daemonParams;
        final BuildLayoutResult buildLayout;
        final AllProperties properties;
        final Map<String, String> tapiSystemProperties;
        final StartParameterInternal startParameter;
        final DaemonRequestContext requestContext;

        public Parameters(DaemonParameters daemonParams, BuildLayoutResult buildLayout, AllProperties properties, Map<String, String> tapiSystemProperties, StartParameterInternal startParameter, DaemonRequestContext requestContext) {
            this.daemonParams = daemonParams;
            this.buildLayout = buildLayout;
            this.properties = properties;
            this.tapiSystemProperties = tapiSystemProperties;
            this.startParameter = startParameter;
            this.requestContext = requestContext;
        }
    }

    private static final class BuildProgressListenerInvokingBuildEventConsumer implements BuildEventConsumer {
        private final InternalBuildProgressListener buildProgressListener;

        private BuildProgressListenerInvokingBuildEventConsumer(InternalBuildProgressListener buildProgressListener) {
            this.buildProgressListener = buildProgressListener;
        }

        @Override
        public void dispatch(Object event) {
            if (event instanceof InternalProgressEvent) {
                this.buildProgressListener.onEvent(event);
            }
        }
    }

    @VisibleForTesting
    static final class ProgressListenerConfiguration {

        private static final Map<String, OperationType> OPERATION_TYPE_MAPPING = ImmutableMap.<String, OperationType>builderWithExpectedSize(OperationType.values().length)
            .put(InternalBuildProgressListener.TEST_EXECUTION, OperationType.TEST)
            .put(InternalBuildProgressListener.TASK_EXECUTION, OperationType.TASK)
            .put(InternalBuildProgressListener.WORK_ITEM_EXECUTION, OperationType.WORK_ITEM)
            .put(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION, OperationType.PROJECT_CONFIGURATION)
            .put(InternalBuildProgressListener.TRANSFORM_EXECUTION, OperationType.TRANSFORM)
            .put(InternalBuildProgressListener.BUILD_EXECUTION, OperationType.GENERIC)
            .put(InternalBuildProgressListener.TEST_OUTPUT, OperationType.TEST_OUTPUT)
            .put(InternalBuildProgressListener.FILE_DOWNLOAD, OperationType.FILE_DOWNLOAD)
            .put(InternalBuildProgressListener.BUILD_PHASE, OperationType.BUILD_PHASE)
            .put(InternalBuildProgressListener.PROBLEMS, OperationType.PROBLEMS)
            .build();

        private final BuildEventSubscriptions clientSubscriptions;
        private final FailsafeBuildProgressListenerAdapter failsafeWrapper;
        private final BuildEventConsumer buildEventConsumer;

        ProgressListenerConfiguration(BuildEventSubscriptions clientSubscriptions, BuildEventConsumer buildEventConsumer, FailsafeBuildProgressListenerAdapter failsafeWrapper) {
            this.clientSubscriptions = clientSubscriptions;
            this.buildEventConsumer = buildEventConsumer;
            this.failsafeWrapper = failsafeWrapper;
        }

        @VisibleForTesting
        BuildEventSubscriptions getClientSubscriptions() {
            return clientSubscriptions;
        }

        @VisibleForTesting
        static ProgressListenerConfiguration from(
            ProviderOperationParameters providerParameters,
            GradleVersion consumerVersion,
            PayloadSerializer payloadSerializer
        ) {
            InternalBuildProgressListener buildProgressListener = providerParameters.getBuildProgressListener();
            Set<OperationType> operationTypes = toOperationTypes(buildProgressListener, consumerVersion);
            BuildEventSubscriptions clientSubscriptions = new BuildEventSubscriptions(operationTypes);
            FailsafeBuildProgressListenerAdapter progressListenerAdapter = new FailsafeBuildProgressListenerAdapter(buildProgressListener);
            BuildEventConsumer buildEventConsumer = clientSubscriptions.isAnyOperationTypeRequested() ? new BuildProgressListenerInvokingBuildEventConsumer(progressListenerAdapter) : new NoOpBuildEventConsumer();
            buildEventConsumer = new StreamedValueConsumer(providerParameters, payloadSerializer, buildEventConsumer);
            if (Boolean.TRUE.equals(providerParameters.isEmbedded())) {
                // Contract requires build events are delivered by a single thread. This is taken care of by the daemon client when not in embedded mode
                // Need to apply some synchronization when in embedded mode
                buildEventConsumer = new SynchronizedConsumer(buildEventConsumer);
            }
            return new ProgressListenerConfiguration(clientSubscriptions, buildEventConsumer, progressListenerAdapter);
        }

        private static Set<OperationType> toOperationTypes(InternalBuildProgressListener buildProgressListener, GradleVersion consumerVersion) {
            if (buildProgressListener != null) {
                Set<OperationType> operationTypes = EnumSet.noneOf(OperationType.class);
                for (String operation : buildProgressListener.getSubscribedOperations()) {
                    if (OPERATION_TYPE_MAPPING.containsKey(operation)) {
                        operationTypes.add(OPERATION_TYPE_MAPPING.get(operation));
                    }
                }
                if (consumerVersion.compareTo(GradleVersion.version("5.1")) < 0) {
                    // Some types were split out of 'generic' type in 5.1, so include these when an older consumer requests 'generic'
                    if (operationTypes.contains(OperationType.GENERIC)) {
                        operationTypes.add(OperationType.PROJECT_CONFIGURATION);
                        operationTypes.add(OperationType.TRANSFORM);
                        operationTypes.add(OperationType.WORK_ITEM);
                    }
                }
                if (consumerVersion.compareTo(GradleVersion.version("7.3")) < 0) {
                    // Some types were split out of 'generic' type in 7.3, so include these when an older consumer requests 'generic'
                    if (operationTypes.contains(OperationType.GENERIC)) {
                        operationTypes.add(OperationType.FILE_DOWNLOAD);
                    }
                }
                return operationTypes;
            }
            return emptySet();
        }

        private static class SynchronizedConsumer implements BuildEventConsumer {
            private final BuildEventConsumer delegate;

            public SynchronizedConsumer(BuildEventConsumer delegate) {
                this.delegate = delegate;
            }

            @Override
            public void dispatch(Object message) {
                synchronized (this) {
                    delegate.dispatch(message);
                }
            }
        }
    }
}
