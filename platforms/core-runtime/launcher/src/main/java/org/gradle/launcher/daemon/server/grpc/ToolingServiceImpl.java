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
import org.gradle.tooling.internal.grpc.proto.BuildConfiguration;
import org.gradle.tooling.internal.grpc.proto.BuildEnvironment;
import org.gradle.tooling.internal.grpc.proto.BuildEvent;
import org.gradle.tooling.internal.grpc.proto.BuildRequest;
import org.gradle.tooling.internal.grpc.proto.BuildResult;
import org.gradle.tooling.internal.grpc.proto.CancelRequest;
import org.gradle.tooling.internal.grpc.proto.CancelResponse;
import org.gradle.tooling.internal.grpc.proto.ConnectRequest;
import org.gradle.tooling.internal.grpc.proto.ConnectResponse;
import org.gradle.tooling.internal.grpc.proto.Failure;
import org.gradle.tooling.internal.grpc.proto.Outcome;
import org.gradle.tooling.internal.grpc.proto.ModelRequest;
import org.gradle.tooling.internal.grpc.proto.ModelResponse;
import org.gradle.tooling.internal.grpc.proto.ModelType;
import org.gradle.tooling.internal.grpc.proto.OutputLine;
import org.gradle.tooling.internal.grpc.proto.Span;
import org.gradle.tooling.internal.grpc.proto.StyledOutput;
import org.gradle.tooling.internal.grpc.proto.ToolingGrpc;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.gradle.tooling.internal.provider.action.GrpcModelQueryAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    // The contract version this endpoint speaks, and the capability flags it advertises. The in-daemon
    // server offers the full surface; the cross-version bridge advertises a subset for old daemons.
    private static final int CONTRACT_VERSION = 1;
    private static final List<String> CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
        "build.run",
        "control.cancel",
        "models.build_environment",
        "models.plugin",
        "models.project_targeting",
        "models.parameterized"
    ));

    private final BuildExecutor buildExecutor;
    private final LoggingOutputInternal loggingOutput;
    private final DaemonStateControl stateControl;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradleUserHomeScopeServiceRegistry userHomeServiceRegistry;
    // The build_id of the RunBuild currently executing, so a concurrent Cancel can target it. The
    // daemon runs one build at a time, so a single slot is enough.
    private final AtomicReference<String> runningBuildId = new AtomicReference<>();

    public ToolingServiceImpl(BuildExecutor buildExecutor, LoggingOutputInternal loggingOutput, DaemonStateControl stateControl, BuildLayoutFactory buildLayoutFactory, GradleUserHomeScopeServiceRegistry userHomeServiceRegistry) {
        this.buildExecutor = buildExecutor;
        this.loggingOutput = loggingOutput;
        this.stateControl = stateControl;
        this.buildLayoutFactory = buildLayoutFactory;
        this.userHomeServiceRegistry = userHomeServiceRegistry;
    }

    @Override
    public void connect(ConnectRequest request, StreamObserver<ConnectResponse> responseObserver) {
        // Direct mode: the server is in the daemon, so it speaks for this exact Gradle version and
        // offers the full capability set.
        responseObserver.onNext(ConnectResponse.newBuilder()
            .setGradleVersion(GradleVersion.current().getVersion())
            .setContractVersion(CONTRACT_VERSION)
            .addAllCapabilities(CAPABILITIES)
            .build());
        responseObserver.onCompleted();
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

        String buildId = request.getBuildId();
        if (!buildId.isEmpty()) {
            runningBuildId.set(buildId);
        }

        boolean success;
        String message;
        Outcome outcome = Outcome.OUTCOME_UNSPECIFIED;
        Throwable buildFailure = null;
        try {
            // Apply structured build configuration. System properties and the Gradle user home ride
            // Gradle's own CLI converter (-D / --gradle-user-home) and take effect here. Environment
            // variables, java_home and jvm_arguments select the build's process and JVM; the in-daemon
            // path runs the build in this daemon's own JVM and bypasses the daemon's env-applying
            // command step, so those are honoured by the cross-version bridge (which starts a fresh
            // daemon per request) but not on this direct path.
            BuildConfiguration config = request.getConfiguration();
            List<String> args = new ArrayList<>(request.getArgsList());
            for (Map.Entry<String, String> property : config.getSystemPropertiesMap().entrySet()) {
                args.add("-D" + property.getKey() + "=" + property.getValue());
            }
            if (!config.getGradleUserHome().isEmpty()) {
                args.add("--gradle-user-home");
                args.add(config.getGradleUserHome());
            }

            StartParameterInternal startParameter = toStartParameter(args, new File(request.getProjectDir()));
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
            boolean cancelled = result != null && result.wasCancelled();
            message = success ? "BUILD SUCCESSFUL" : cancelled ? "BUILD CANCELLED" : "BUILD FAILED";
            outcome = success ? Outcome.OUTCOME_SUCCESS : cancelled ? Outcome.OUTCOME_CANCELLED : Outcome.OUTCOME_FAILED;
            if (!success && !cancelled && result != null) {
                // The build failure is usually serialized (getException() is null); deserialize it the
                // same way a model result is, to recover the exception tree.
                buildFailure = result.getException();
                if (buildFailure == null && result.getFailure() != null) {
                    Object deserialized = deserializeResult(startParameter, result.getFailure());
                    if (deserialized instanceof Throwable) {
                        buildFailure = (Throwable) deserialized;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("gRPC tooling API build failed to execute", t);
            success = false;
            message = "Build failed to run: " + t.getMessage();
            outcome = Outcome.OUTCOME_FAILED;
            buildFailure = t;
        } finally {
            loggingOutput.removeOutputEventListener(listener);
            if (!buildId.isEmpty()) {
                runningBuildId.compareAndSet(buildId, null);
            }
        }

        BuildResult.Builder result = BuildResult.newBuilder().setSuccess(success).setMessage(message).setOutcome(outcome);
        if (buildFailure != null) {
            result.addFailures(toFailure(buildFailure));
        }
        synchronized (responseLock) {
            responseObserver.onNext(BuildEvent.newBuilder().setResult(result).build());
            responseObserver.onCompleted();
        }
    }

    private static Failure toFailure(Throwable t) {
        Failure.Builder failure = Failure.newBuilder()
            .setMessage(t.getMessage() != null ? t.getMessage() : "")
            .setExceptionClass(t.getClass().getName());
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            failure.addCauses(toFailure(cause));
        }
        return failure.build();
    }

    @Override
    public void cancel(CancelRequest request, StreamObserver<CancelResponse> responseObserver) {
        // Map a Tooling API cancellation onto the daemon's own cancel path: requestCancel() trips the
        // BuildCancellationToken the running build (and its tasks) observe. Guard on the build id so a
        // stale cancel cannot stop a later build that reused the daemon.
        String buildId = request.getBuildId();
        boolean matches = !buildId.isEmpty() && buildId.equals(runningBuildId.get());
        if (matches) {
            stateControl.requestCancel();
        }
        responseObserver.onNext(CancelResponse.newBuilder()
            .setCancelled(matches)
            .setMessage(matches
                ? "Cancellation requested for build " + buildId
                : "No running build with id '" + buildId + "'")
            .build());
        responseObserver.onCompleted();
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

        // Target a specific project by its logical path (e.g. ":app") when the client asks for one,
        // the way a Tooling API BuildController.getModel(project, type) call does. The client stays
        // connected to the build root; it does not point its connection at the subproject directory.
        // An optional Any parameter is forwarded opaquely for a ParameterizedToolingModelBuilder.
        byte[] parameterBytes = request.hasParameter() ? request.getParameter().toByteArray() : new byte[0];
        BuildAction action = new GrpcModelQueryAction(startParameter, request.getModelName(), projectDir, request.getProjectPath(), parameterBytes, new BuildEventSubscriptions(Collections.emptySet()));
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
        Any any;
        if (model instanceof com.google.protobuf.Message) {
            // The builder returned a protobuf message directly. This works because the plugin and the
            // daemon share one com.google.protobuf.Message class (protobuf-java is exported to
            // plugins), so the same builder also serves a JVM Tooling API client that receives the
            // message via the normal serialize/adapt path.
            any = Any.pack((com.google.protobuf.Message) model);
        } else if (model instanceof byte[]) {
            // The builder returned the serialized bytes of a google.protobuf.Any (no shared protobuf).
            any = Any.parseFrom((byte[]) model);
        } else {
            return ModelResponse.newBuilder().setSuccess(false)
                .setError("Model '" + request.getModelName() + "' did not return a protobuf message or Any bytes (got "
                    + (model == null ? "null" : model.getClass().getName()) + ").").build();
        }
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
