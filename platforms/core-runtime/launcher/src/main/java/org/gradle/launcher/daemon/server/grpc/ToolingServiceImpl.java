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

import io.grpc.stub.StreamObserver;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.LoggingOutputInternal;
import org.gradle.internal.logging.events.LogEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecutor;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.grpc.proto.BuildEnvironment;
import org.gradle.tooling.grpc.proto.BuildEvent;
import org.gradle.tooling.grpc.proto.BuildRequest;
import org.gradle.tooling.grpc.proto.BuildResult;
import org.gradle.tooling.grpc.proto.ModelRequest;
import org.gradle.tooling.grpc.proto.ModelResponse;
import org.gradle.tooling.grpc.proto.OutputLine;
import org.gradle.tooling.grpc.proto.Span;
import org.gradle.tooling.grpc.proto.StyledOutput;
import org.gradle.tooling.grpc.proto.ToolingGrpc;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.gradle.util.GradleVersion;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives a build from a gRPC {@code RunBuild} request by reusing the daemon's {@link BuildExecutor}
 * (as {@code ExecuteBuild} does for the Kryo protocol) and streams build output by registering an
 * {@link OutputEventListener} on the daemon's logging output (mirroring {@code LogToClient}).
 * Also answers {@code QueryModel} requests about build state (the "C" slice).
 */
public class ToolingServiceImpl extends ToolingGrpc.ToolingImplBase {

    private static final Logger LOGGER = Logging.getLogger(ToolingServiceImpl.class);
    private static final BuildEventConsumer NO_OP_EVENT_CONSUMER = event -> {
    };

    private final BuildExecutor buildExecutor;
    private final LoggingOutputInternal loggingOutput;
    private final DaemonStateControl stateControl;

    public ToolingServiceImpl(BuildExecutor buildExecutor, LoggingOutputInternal loggingOutput, DaemonStateControl stateControl) {
        this.buildExecutor = buildExecutor;
        this.loggingOutput = loggingOutput;
        this.stateControl = stateControl;
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
        // The "C" slice: answer a build-state question over gRPC, language-neutrally, no TAPI.
        // Only BuildEnvironment for now; richer models (tasks/dependencies) need a model-projection
        // layer (the documented hard part) and are a follow-up.
        try {
            BuildEnvironment env = BuildEnvironment.newBuilder()
                .setGradleVersion(GradleVersion.current().getVersion())
                .setJavaHome(System.getProperty("java.home", ""))
                .setJavaVersion(Integer.parseInt(JavaVersion.current().getMajorVersion()))
                .build();
            responseObserver.onNext(ModelResponse.newBuilder().setSuccess(true).setBuildEnvironment(env).build());
        } catch (Throwable t) {
            LOGGER.warn("gRPC tooling API model query failed", t);
            responseObserver.onNext(ModelResponse.newBuilder().setSuccess(false).setError(String.valueOf(t.getMessage())).build());
        }
        responseObserver.onCompleted();
    }

    private static @Nullable BuildEvent toBuildEvent(OutputEvent event) {
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

    private static StartParameterInternal toStartParameter(BuildRequest request) {
        // Pragmatic arg handling: tasks + the common flags. The full CLI converter chain
        // (all options, gradle.properties merge, init scripts) is a documented follow-up.
        StartParameterInternal startParameter = new StartParameterInternal();
        startParameter.setCurrentDir(new File(request.getProjectDir()));

        List<String> tasks = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        Map<String, String> projectProperties = new HashMap<>();
        Map<String, String> systemProperties = new HashMap<>();

        List<String> args = request.getArgsList();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("--info")) {
                startParameter.setLogLevel(LogLevel.INFO);
            } else if (arg.equals("--debug") || arg.equals("-d")) {
                startParameter.setLogLevel(LogLevel.DEBUG);
            } else if (arg.equals("--quiet") || arg.equals("-q")) {
                startParameter.setLogLevel(LogLevel.QUIET);
            } else if (arg.equals("--warn") || arg.equals("-w")) {
                startParameter.setLogLevel(LogLevel.WARN);
            } else if (arg.equals("--rerun-tasks")) {
                startParameter.setRerunTasks(true);
            } else if (arg.equals("-x") || arg.equals("--exclude-task")) {
                if (i + 1 < args.size()) {
                    excluded.add(args.get(++i));
                }
            } else if (arg.startsWith("-P")) {
                putKeyValue(projectProperties, arg.substring(2));
            } else if (arg.startsWith("-D")) {
                putKeyValue(systemProperties, arg.substring(2));
            } else {
                tasks.add(arg);
            }
        }

        if (!tasks.isEmpty()) {
            startParameter.setTaskNames(tasks);
        }
        if (!excluded.isEmpty()) {
            startParameter.setExcludedTaskNames(excluded);
        }
        if (!projectProperties.isEmpty()) {
            startParameter.setProjectProperties(projectProperties);
        }
        if (!systemProperties.isEmpty()) {
            startParameter.setSystemPropertiesArgs(systemProperties);
        }
        return startParameter;
    }

    private static void putKeyValue(Map<String, String> target, String keyValue) {
        int eq = keyValue.indexOf('=');
        if (eq < 0) {
            target.put(keyValue, "true");
        } else {
            target.put(keyValue.substring(0, eq), keyValue.substring(eq + 1));
        }
    }

    private static boolean isUserVisible(org.gradle.api.logging.@Nullable LogLevel level) {
        // Stream only build-facing output (LIFECYCLE and above), not daemon/netty DEBUG/INFO noise.
        // LogLevel order: DEBUG, INFO, LIFECYCLE, WARN, QUIET, ERROR.
        return level == null || level.compareTo(LogLevel.LIFECYCLE) >= 0;
    }

    private static org.gradle.tooling.grpc.proto.LogLevel mapLevel(org.gradle.api.logging.@Nullable LogLevel level) {
        if (level == null) {
            return org.gradle.tooling.grpc.proto.LogLevel.LOG_LEVEL_UNSPECIFIED;
        }
        switch (level) {
            case QUIET:
                return org.gradle.tooling.grpc.proto.LogLevel.QUIET;
            case WARN:
                return org.gradle.tooling.grpc.proto.LogLevel.WARN;
            case LIFECYCLE:
                return org.gradle.tooling.grpc.proto.LogLevel.LIFECYCLE;
            case INFO:
                return org.gradle.tooling.grpc.proto.LogLevel.INFO;
            case DEBUG:
                return org.gradle.tooling.grpc.proto.LogLevel.DEBUG;
            case ERROR:
                return org.gradle.tooling.grpc.proto.LogLevel.ERROR;
            default:
                return org.gradle.tooling.grpc.proto.LogLevel.LOG_LEVEL_UNSPECIFIED;
        }
    }

    private static org.gradle.tooling.grpc.proto.Style mapStyle(StyledTextOutput.Style style) {
        switch (style) {
            case Header:
                return org.gradle.tooling.grpc.proto.Style.STYLE_HEADER;
            case UserInput:
                return org.gradle.tooling.grpc.proto.Style.STYLE_USER_INPUT;
            case Identifier:
                return org.gradle.tooling.grpc.proto.Style.STYLE_IDENTIFIER;
            case Description:
                return org.gradle.tooling.grpc.proto.Style.STYLE_DESCRIPTION;
            case ProgressStatus:
                return org.gradle.tooling.grpc.proto.Style.STYLE_PROGRESS_STATUS;
            case Success:
                return org.gradle.tooling.grpc.proto.Style.STYLE_SUCCESS;
            case SuccessHeader:
                return org.gradle.tooling.grpc.proto.Style.STYLE_SUCCESS_HEADER;
            case Failure:
                return org.gradle.tooling.grpc.proto.Style.STYLE_FAILURE;
            case FailureHeader:
                return org.gradle.tooling.grpc.proto.Style.STYLE_FAILURE_HEADER;
            case Info:
                return org.gradle.tooling.grpc.proto.Style.STYLE_INFO;
            case Error:
                return org.gradle.tooling.grpc.proto.Style.STYLE_ERROR;
            case Normal:
            default:
                return org.gradle.tooling.grpc.proto.Style.STYLE_NORMAL;
        }
    }
}
