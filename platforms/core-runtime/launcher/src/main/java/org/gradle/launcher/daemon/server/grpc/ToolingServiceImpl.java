/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.server.grpc;

import com.google.protobuf.Any;
import io.grpc.stub.StreamObserver;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecutor;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.internal.grpc.proto.BuildEnvironment;
import org.gradle.tooling.internal.grpc.proto.BuildEvent;
import org.gradle.tooling.internal.grpc.proto.BuildRequest;
import org.gradle.tooling.internal.grpc.proto.BuildResult;
import org.gradle.tooling.internal.grpc.proto.ModelRequest;
import org.gradle.tooling.internal.grpc.proto.ModelResponse;
import org.gradle.tooling.internal.grpc.proto.ModelType;
import org.gradle.tooling.internal.grpc.proto.OutputLine;
import org.gradle.tooling.internal.grpc.proto.Span;
import org.gradle.tooling.internal.grpc.proto.StyledOutput;
import org.gradle.tooling.internal.grpc.proto.ToolingGrpc;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives a build from a gRPC {@code RunBuild} request by reusing the daemon's {@link BuildExecutor}
 * and streams build output (log, styled, progress) by registering an {@link OutputEventListener} on
 * the daemon's logging output. Also answers {@code QueryModel} requests about build state.
 */
public class ToolingServiceImpl extends ToolingGrpc.ToolingImplBase {

    private static final Logger LOGGER = Logging.getLogger(ToolingServiceImpl.class);
    private static final BuildEventConsumer NO_OP_EVENT_CONSUMER = event -> {
    };

    private final BuildExecutor buildExecutor;
    private final LoggingOutputInternal loggingOutput;
    private final DaemonStateControl stateControl;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradleUserHomeScopeServiceRegistry userHomeServiceRegistry;

    public ToolingServiceImpl(BuildExecutor buildExecutor, LoggingOutputInternal loggingOutput, DaemonStateControl stateControl, BuildLayoutFactory buildLayoutFactory, GradleUserHomeScopeServiceRegistry userHomeServiceRegistry) {
        this.buildExecutor = buildExecutor;
        this.loggingOutput = loggingOutput;
        this.stateControl = stateControl;
        this.buildLayoutFactory = buildLayoutFactory;
        this.userHomeServiceRegistry = userHomeServiceRegistry;
    }

    @Override
    public void runBuild(BuildRequest request, StreamObserver<BuildEvent> responseObserver) {
        // gRPC requires all calls onto the response observer to be serialized; the output listener
        // fires from arbitrary logging threads, so guard every onNext/onCompleted with this monitor.
        Object responseLock = new Object();

        OutputEventListener listener = event -> {
            BuildEvent message = toBuildEvent(event);
            if (message != null) {
                synchronized (responseLock) {
                    responseObserver.onNext(message);
                }
            }
        };
        loggingOutput.addOutputEventListener(listener);

        boolean success;
        String message;
        try {
            StartParameterInternal startParameter = toStartParameter(request);
            BuildAction action = new ExecuteBuildAction(startParameter);
            BuildActionParameters parameters = new DefaultBuildActionParameters(
                System.getProperties(),
                System.getenv(),
                new File(request.getProjectDir()),
                startParameter.getLogLevel(),
                false,
                ClassPath.EMPTY);

            AtomicReference<BuildActionResult> resultRef = new AtomicReference<>();
            stateControl.runCommand(() -> {
                BuildRequestContext context = new DefaultBuildRequestContext(
                    new DefaultBuildRequestMetaData(new DefaultBuildClientMetaData(new GradleLauncherMetaData()), System.currentTimeMillis(), false),
                    stateControl.getCancellationToken(),
                    NO_OP_EVENT_CONSUMER);
                resultRef.set(buildExecutor.execute(action, parameters, context));
            }, "gRPC tooling API build");

            BuildActionResult result = resultRef.get();
            success = result != null && !result.hasFailure() && !result.wasCancelled();
            message = success ? "BUILD SUCCESSFUL" : "BUILD FAILED";
        } catch (Throwable t) {
            LOGGER.warn("gRPC tooling API build failed to execute", t);
            success = false;
            message = "Build failed to run: " + t.getMessage();
        } finally {
            loggingOutput.removeOutputEventListener(listener);
        }

        synchronized (responseLock) {
            responseObserver.onNext(BuildEvent.newBuilder()
                .setResult(BuildResult.newBuilder().setSuccess(success).setMessage(message).build())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void queryModel(ModelRequest request, StreamObserver<ModelResponse> responseObserver) {
        try {
            if (!request.getModelName().isEmpty()) {
                // Plugin-contributed model: run it through the real build-model pipeline and forward
                // the plugin's protobuf Any bytes to the client, wrapped in ModelResponse.model_any.
                responseObserver.onNext(queryPluginModel(request));
            } else if (request.getType() == ModelType.MODEL_TASKS) {
                responseObserver.onNext(ModelResponse.newBuilder()
                    .setSuccess(false)
                    .setError("MODEL_TASKS is not implemented; query a plugin-contributed model by name instead.")
                    .build());
            } else {
                BuildEnvironment env = BuildEnvironment.newBuilder()
                    .setGradleVersion(GradleVersion.current().getVersion())
                    .setJavaHome(System.getProperty("java.home", ""))
                    .setJavaVersion(Integer.parseInt(JavaVersion.current().getMajorVersion()))
                    .build();
                responseObserver.onNext(ModelResponse.newBuilder().setSuccess(true).setBuildEnvironment(env).build());
            }
        } catch (Throwable t) {
            LOGGER.warn("gRPC tooling API model query failed", t);
            responseObserver.onNext(ModelResponse.newBuilder().setSuccess(false).setError(String.valueOf(t.getMessage())).build());
        }
        responseObserver.onCompleted();
    }

    /**
     * Answers a query for a plugin-contributed model. Runs a {@link BuildModelAction} through the
     * daemon's {@link BuildExecutor} - the same pipeline the Kryo/TAPI model request uses, which
     * configures the target project and invokes the registered {@code ToolingModelBuilder}. The
     * builder returns the bytes of a {@code google.protobuf.Any}; we deserialize that result and
     * forward the Any to the client. The daemon never references the plugin's model type.
     */
    private ModelResponse queryPluginModel(ModelRequest request) throws Exception {
        File projectDir = new File(request.getProjectDir());
        StartParameterInternal startParameter = toStartParameter(Collections.<String>emptyList(), projectDir);

        BuildAction action = new BuildModelAction(startParameter, request.getModelName(), false, new BuildEventSubscriptions(Collections.emptySet()));
        BuildActionParameters parameters = new DefaultBuildActionParameters(
            System.getProperties(),
            System.getenv(),
            projectDir,
            startParameter.getLogLevel(),
            false,
            ClassPath.EMPTY);

        AtomicReference<BuildActionResult> resultRef = new AtomicReference<>();
        stateControl.runCommand(() -> {
            BuildRequestContext context = new DefaultBuildRequestContext(
                new DefaultBuildRequestMetaData(new DefaultBuildClientMetaData(new GradleLauncherMetaData()), System.currentTimeMillis(), false),
                stateControl.getCancellationToken(),
                NO_OP_EVENT_CONSUMER);
            resultRef.set(buildExecutor.execute(action, parameters, context));
        }, "gRPC tooling API model query");

        BuildActionResult result = resultRef.get();
        if (result == null || result.hasFailure()) {
            RuntimeException failure = result == null ? null : result.getException();
            String error = failure != null ? String.valueOf(failure.getMessage()) : "Model query failed";
            return ModelResponse.newBuilder().setSuccess(false).setError(error).build();
        }

        Object model = deserializeResult(startParameter, result.getResult());
        if (!(model instanceof byte[])) {
            return ModelResponse.newBuilder().setSuccess(false)
                .setError("Model '" + request.getModelName() + "' did not return protobuf Any bytes (got "
                    + (model == null ? "null" : model.getClass().getName()) + ").").build();
        }
        Any any = Any.parseFrom((byte[]) model);
        return ModelResponse.newBuilder().setSuccess(true).setModelAny(any).build();
    }

    /**
     * Deserializes a build-model result. The result was serialized inside the build session by the
     * build-scoped {@link PayloadSerializer}; here at the daemon-global gRPC layer we obtain a
     * {@link PayloadSerializer} through the user-home service registry to reconstruct it. For a
     * {@code byte[]} model this only touches JDK classloaders, so no plugin classes are needed.
     */
    private @Nullable Object deserializeResult(StartParameterInternal startParameter, @Nullable SerializedPayload payload) {
        if (payload == null) {
            return null;
        }
        ServiceRegistry services = userHomeServiceRegistry.getServicesFor(startParameter.getGradleUserHomeDir());
        try {
            return services.get(PayloadSerializer.class).deserialize(payload);
        } finally {
            userHomeServiceRegistry.release(services);
        }
    }

    private static @Nullable BuildEvent toBuildEvent(OutputEvent event) {
        // Progress events are structural UI signals; stream them regardless of log level.
        if (event instanceof ProgressStartEvent) {
            ProgressStartEvent start = (ProgressStartEvent) event;
            return progress(org.gradle.tooling.internal.grpc.proto.ProgressType.PROGRESS_START, start.getDescription(), start.getStatus());
        }
        if (event instanceof ProgressCompleteEvent) {
            return progress(org.gradle.tooling.internal.grpc.proto.ProgressType.PROGRESS_COMPLETE, "", ((ProgressCompleteEvent) event).getStatus());
        }
        if (event instanceof ProgressEvent) {
            return progress(org.gradle.tooling.internal.grpc.proto.ProgressType.PROGRESS_STATUS, "", ((ProgressEvent) event).getStatus());
        }
        // Log/styled output: only build-facing levels (LIFECYCLE+), not daemon/netty DEBUG/INFO noise.
        if (!isUserVisible(event.getLogLevel())) {
            return null;
        }
        if (event instanceof LogEvent) {
            String text = ((LogEvent) event).getMessage();
            return BuildEvent.newBuilder()
                .setOutput(OutputLine.newBuilder().setText(text).setLevel(mapLevel(event.getLogLevel())).build())
                .build();
        }
        if (event instanceof StyledTextOutputEvent) {
            StyledOutput.Builder styled = StyledOutput.newBuilder().setLevel(mapLevel(event.getLogLevel()));
            for (StyledTextOutputEvent.Span span : ((StyledTextOutputEvent) event).getSpans()) {
                styled.addSpans(Span.newBuilder().setText(span.getText()).setStyle(mapStyle(span.getStyle())).build());
            }
            return BuildEvent.newBuilder().setStyled(styled.build()).build();
        }
        return null;
    }

    private static BuildEvent progress(org.gradle.tooling.internal.grpc.proto.ProgressType type, @Nullable String description, @Nullable String status) {
        return BuildEvent.newBuilder()
            .setProgress(org.gradle.tooling.internal.grpc.proto.ProgressEvent.newBuilder()
                .setType(type)
                .setDescription(description == null ? "" : description)
                .setStatus(status == null ? "" : status)
                .build())
            .build();
    }

    private StartParameterInternal toStartParameter(BuildRequest request) {
        return toStartParameter(request.getArgsList(), new File(request.getProjectDir()));
    }

    private StartParameterInternal toStartParameter(List<String> args, File projectDir) {
        // Reuse Gradle's real CLI converter chain so all flags, -P/-D, gradle.properties merge, etc. apply.
        CommandLineParser parser = new CommandLineParser();
        parser.allowUnknownOptions();
        parser.allowMixedSubcommandsAndOptions();

        InitialPropertiesConverter initialPropertiesConverter = new InitialPropertiesConverter();
        BuildLayoutConverter buildLayoutConverter = new BuildLayoutConverter();
        StartParameterConverter startParameterConverter = new StartParameterConverter();
        initialPropertiesConverter.configure(parser);
        buildLayoutConverter.configure(parser);
        startParameterConverter.configure(parser);

        ParsedCommandLine parsed = parser.parse(args);
        InitialProperties initialProperties = initialPropertiesConverter.convert(parsed);
        BuildLayoutResult layout = buildLayoutConverter.convert(initialProperties, parsed, projectDir);
        AllProperties properties = new LayoutToPropertiesConverter(buildLayoutFactory).convert(initialProperties, layout);

        StartParameterInternal startParameter = new StartParameterInternal();
        startParameterConverter.convert(parsed, layout, properties, System.getenv(), startParameter);
        return startParameter;
    }

    private static boolean isUserVisible(org.gradle.api.logging.@Nullable LogLevel level) {
        // LogLevel order: DEBUG, INFO, LIFECYCLE, WARN, QUIET, ERROR.
        return level == null || level.compareTo(LogLevel.LIFECYCLE) >= 0;
    }

    private static org.gradle.tooling.internal.grpc.proto.LogLevel mapLevel(org.gradle.api.logging.@Nullable LogLevel level) {
        if (level == null) {
            return org.gradle.tooling.internal.grpc.proto.LogLevel.LOG_LEVEL_UNSPECIFIED;
        }
        switch (level) {
            case QUIET:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.QUIET;
            case WARN:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.WARN;
            case LIFECYCLE:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.LIFECYCLE;
            case INFO:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.INFO;
            case DEBUG:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.DEBUG;
            case ERROR:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.ERROR;
            default:
                return org.gradle.tooling.internal.grpc.proto.LogLevel.LOG_LEVEL_UNSPECIFIED;
        }
    }

    private static org.gradle.tooling.internal.grpc.proto.Style mapStyle(StyledTextOutput.Style style) {
        switch (style) {
            case Header:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_HEADER;
            case UserInput:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_USER_INPUT;
            case Identifier:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_IDENTIFIER;
            case Description:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_DESCRIPTION;
            case ProgressStatus:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_PROGRESS_STATUS;
            case Success:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_SUCCESS;
            case SuccessHeader:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_SUCCESS_HEADER;
            case Failure:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_FAILURE;
            case FailureHeader:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_FAILURE_HEADER;
            case Info:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_INFO;
            case Error:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_ERROR;
            case Normal:
            default:
                return org.gradle.tooling.internal.grpc.proto.Style.STYLE_NORMAL;
        }
    }
}
