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
import org.gradle.api.internal.StartParameterInternal;
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
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.launcher.exec.BuildExecutor;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.tooling.grpc.proto.BuildEvent;
import org.gradle.tooling.grpc.proto.BuildRequest;
import org.gradle.tooling.grpc.proto.BuildResult;
import org.gradle.tooling.grpc.proto.OutputLine;
import org.gradle.tooling.grpc.proto.ToolingGrpc;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives a build from a gRPC {@code RunBuild} request by reusing the daemon's {@link BuildExecutor},
 * exactly as {@code ExecuteBuild} does for the Kryo protocol, and streams the build output back by
 * registering an {@link OutputEventListener} on the daemon's logging output (mirroring {@code LogToClient}).
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
            String text = flatten(event);
            if (text != null && isUserVisible(event.getLogLevel())) {
                synchronized (responseLock) {
                    responseObserver.onNext(BuildEvent.newBuilder()
                        .setOutput(OutputLine.newBuilder()
                            .setText(text)
                            .setLevel(mapLevel(event.getLogLevel()))
                            .build())
                        .build());
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
                org.gradle.api.logging.LogLevel.LIFECYCLE,
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

    private static StartParameterInternal toStartParameter(BuildRequest request) {
        // Prototype: treat each arg as a task name. Reusing the full CLI converter chain
        // (CommandLineParser + StartParameterConverter) for flags/-P/-D is a documented follow-up.
        StartParameterInternal startParameter = new StartParameterInternal();
        startParameter.setCurrentDir(new File(request.getProjectDir()));
        if (!request.getArgsList().isEmpty()) {
            startParameter.setTaskNames(request.getArgsList());
        }
        return startParameter;
    }

    private static @Nullable String flatten(OutputEvent event) {
        if (event instanceof LogEvent) {
            return ((LogEvent) event).getMessage();
        }
        if (event instanceof StyledTextOutputEvent) {
            StringBuilder builder = new StringBuilder();
            for (StyledTextOutputEvent.Span span : ((StyledTextOutputEvent) event).getSpans()) {
                builder.append(span.getText());
            }
            return builder.toString();
        }
        return null;
    }

    private static boolean isUserVisible(org.gradle.api.logging.@Nullable LogLevel level) {
        // Stream only build-facing output (LIFECYCLE and above), not daemon/netty DEBUG/INFO noise.
        // LogLevel order: DEBUG, INFO, LIFECYCLE, WARN, QUIET, ERROR.
        return level == null || level.compareTo(org.gradle.api.logging.LogLevel.LIFECYCLE) >= 0;
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
}
