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
package com.example.bridge;

import io.grpc.stub.StreamObserver;
import org.gradle.tooling.BuildCancelledException;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.grpc.proto.BuildConfiguration;
import org.gradle.tooling.internal.grpc.proto.BuildEvent;
import org.gradle.tooling.internal.grpc.proto.BuildRequest;
import org.gradle.tooling.internal.grpc.proto.BuildResult;
import org.gradle.tooling.internal.grpc.proto.CancelRequest;
import org.gradle.tooling.internal.grpc.proto.CancelResponse;
import org.gradle.tooling.internal.grpc.proto.ConnectRequest;
import org.gradle.tooling.internal.grpc.proto.ConnectResponse;
import org.gradle.tooling.internal.grpc.proto.Failure;
import org.gradle.tooling.internal.grpc.proto.InputAck;
import org.gradle.tooling.internal.grpc.proto.InputChunk;
import org.gradle.tooling.internal.grpc.proto.LogLevel;
import org.gradle.tooling.internal.grpc.proto.ModelRequest;
import org.gradle.tooling.internal.grpc.proto.ModelResponse;
import org.gradle.tooling.internal.grpc.proto.OperationDescriptor;
import org.gradle.tooling.internal.grpc.proto.OperationFinished;
import org.gradle.tooling.internal.grpc.proto.OperationResult;
import org.gradle.tooling.internal.grpc.proto.OperationStarted;
import org.gradle.tooling.internal.grpc.proto.OperationType;
import org.gradle.tooling.internal.grpc.proto.Outcome;
import org.gradle.tooling.internal.grpc.proto.OutputLine;
import org.gradle.tooling.internal.grpc.proto.ProblemEvent;
import org.gradle.tooling.internal.grpc.proto.Severity;
import org.gradle.tooling.internal.grpc.proto.TaskOperationDetails;
import org.gradle.tooling.internal.grpc.proto.ProgressEvent;
import org.gradle.tooling.internal.grpc.proto.ProgressType;
import org.gradle.tooling.internal.grpc.proto.ToolingGrpc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serves the gRPC tooling contract by delegating to the classic Tooling API. Each RPC opens a
 * {@link ProjectConnection} against the configured target Gradle version - which need not know
 * anything about gRPC - runs the operation, and translates the Tooling API's JVM objects and callback
 * events back into the wire's protobuf messages. This is the "unpack the objects and wire them to
 * gRPC" bridge that lets a non-JVM client reach any version the Tooling API supports.
 */
public class BridgeToolingService extends ToolingGrpc.ToolingImplBase {

    private final String gradleVersion;
    private final String gradleInstallation;
    // build_id -> cancellation source for the build currently running under it, so Cancel can reach it.
    private final ConcurrentHashMap<String, CancellationTokenSource> running = new ConcurrentHashMap<>();
    // build_id -> the write end of the build's standard input, so SendStandardInput can feed it.
    private final ConcurrentHashMap<String, PipedOutputStream> stdin = new ConcurrentHashMap<>();

    // The bridge speaks the same contract version but advertises only the subset it can deliver by
    // driving an old daemon through the classic Tooling API - no plugin-model projection, no logical
    // project targeting or parameters. The client sees fewer flags and degrades gracefully.
    private static final int CONTRACT_VERSION = 1;

    public BridgeToolingService(String gradleVersion, String gradleInstallation) {
        this.gradleVersion = gradleVersion;
        this.gradleInstallation = gradleInstallation;
    }

    @Override
    public void connect(ConnectRequest request, StreamObserver<ConnectResponse> responseObserver) {
        responseObserver.onNext(ConnectResponse.newBuilder()
            .setGradleVersion(gradleVersion.isEmpty() ? "(target project's wrapper)" : gradleVersion)
            .setContractVersion(CONTRACT_VERSION)
            .addCapabilities("build.run")
            .addCapabilities("control.cancel")
            .addCapabilities("models.build_environment")
            .addCapabilities("build.stdin")
            .addCapabilities("events.task")
            .addCapabilities("events.problems")
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<InputChunk> sendStandardInput(StreamObserver<InputAck> responseObserver) {
        // Client-streaming: forward each chunk to the write end of the matching build's standard input.
        return new StreamObserver<InputChunk>() {
            private long received;

            @Override
            public void onNext(InputChunk chunk) {
                PipedOutputStream out = stdin.get(chunk.getBuildId());
                if (out == null) {
                    return;
                }
                try {
                    byte[] data = chunk.getData().toByteArray();
                    if (data.length > 0) {
                        out.write(data);
                        out.flush();
                        received += data.length;
                    }
                    if (chunk.getClose()) {
                        out.close();
                    }
                } catch (IOException ignore) {
                    // The build finished or closed its input; nothing more to feed.
                }
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(InputAck.newBuilder().setBytesReceived(received).build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void runBuild(BuildRequest request, StreamObserver<BuildEvent> responseObserver) {
        // gRPC requires calls onto the observer to be serialized; the Tooling API writes output from
        // its own threads, so guard every onNext/onCompleted with this monitor.
        Object lock = new Object();
        String buildId = request.getBuildId();
        CancellationTokenSource cancellation = GradleConnector.newCancellationTokenSource();
        PipedInputStream stdinIn = null;
        if (!buildId.isEmpty()) {
            running.put(buildId, cancellation);
            try {
                PipedOutputStream stdinOut = new PipedOutputStream();
                stdinIn = new PipedInputStream(stdinOut, 64 * 1024);
                stdin.put(buildId, stdinOut);
            } catch (IOException ignore) {
                stdinIn = null;
            }
        }

        BuildConfiguration config = request.getConfiguration();
        GradleConnector connector = newConnector(new File(request.getProjectDir()));
        if (!config.getGradleUserHome().isEmpty()) {
            connector.useGradleUserHomeDir(new File(config.getGradleUserHome()));
        }
        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher launcher = connection.newBuild().withCancellationToken(cancellation.token());
            if (stdinIn != null) {
                launcher.setStandardInput(stdinIn);
            }

            List<String> tasks = new ArrayList<>();
            List<String> arguments = new ArrayList<>();
            for (String arg : request.getArgsList()) {
                (arg.startsWith("-") ? arguments : tasks).add(arg);
            }
            for (Map.Entry<String, String> property : config.getSystemPropertiesMap().entrySet()) {
                arguments.add("-D" + property.getKey() + "=" + property.getValue());
            }
            if (!tasks.isEmpty()) {
                launcher.forTasks(tasks.toArray(new String[0]));
            }
            if (!arguments.isEmpty()) {
                launcher.withArguments(arguments);
            }

            // The bridge honours the full configuration by mapping it onto the classic BuildLauncher,
            // including the build JVM (java_home / jvm_arguments) that the in-daemon path cannot change.
            if (!config.getEnvironmentVariablesMap().isEmpty()) {
                Map<String, String> environment = new HashMap<>(System.getenv());
                environment.putAll(config.getEnvironmentVariablesMap());
                launcher.setEnvironmentVariables(environment);
            }
            if (!config.getJavaHome().isEmpty()) {
                launcher.setJavaHome(new File(config.getJavaHome()));
            }
            if (!config.getJvmArgumentsList().isEmpty()) {
                launcher.addJvmArguments(config.getJvmArgumentsList());
            }

            launcher.setStandardOutput(new LineStream(responseObserver, lock, LogLevel.LIFECYCLE));
            launcher.setStandardError(new LineStream(responseObserver, lock, LogLevel.ERROR));
            launcher.addProgressListener((org.gradle.tooling.ProgressListener) event -> {
                String description = event.getDescription();
                if (description != null && !description.isEmpty()) {
                    synchronized (lock) {
                        responseObserver.onNext(BuildEvent.newBuilder()
                            .setProgress(ProgressEvent.newBuilder()
                                .setType(ProgressType.PROGRESS_START)
                                .setDescription(description)
                                .build())
                            .build());
                    }
                }
            });

            // Structured events: subscribe to the requested kinds and map the Tooling API's typed
            // task events onto the wire operation tree, and Problems API reports onto ProblemEvents.
            java.util.Set<org.gradle.tooling.events.OperationType> eventTypes = java.util.EnumSet.noneOf(org.gradle.tooling.events.OperationType.class);
            if (request.getSubscriptionsList().contains(OperationType.OPERATION_TYPE_TASK)) {
                eventTypes.add(org.gradle.tooling.events.OperationType.TASK);
            }
            if (request.getSubscriptionsList().contains(OperationType.OPERATION_TYPE_PROBLEMS)) {
                eventTypes.add(org.gradle.tooling.events.OperationType.PROBLEMS);
            }
            if (!eventTypes.isEmpty()) {
                launcher.addProgressListener(
                    (org.gradle.tooling.events.ProgressListener) event -> {
                        BuildEvent mapped = toWireEvent(event);
                        if (mapped != null) {
                            synchronized (lock) {
                                responseObserver.onNext(mapped);
                            }
                        }
                    },
                    eventTypes);
            }

            launcher.run();
            emitResult(responseObserver, lock, true, Outcome.OUTCOME_SUCCESS, "BUILD SUCCESSFUL", null);
        } catch (BuildCancelledException e) {
            emitResult(responseObserver, lock, false, Outcome.OUTCOME_CANCELLED, "BUILD CANCELLED", null);
        } catch (Exception e) {
            emitResult(responseObserver, lock, false, Outcome.OUTCOME_FAILED, "BUILD FAILED: " + rootMessage(e), e);
        } finally {
            if (!buildId.isEmpty()) {
                running.remove(buildId);
                PipedOutputStream stdinOut = stdin.remove(buildId);
                if (stdinOut != null) {
                    try {
                        stdinOut.close();
                    } catch (IOException ignore) {
                        // already closed by the input feeder
                    }
                }
            }
        }
        synchronized (lock) {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void cancel(CancelRequest request, StreamObserver<CancelResponse> responseObserver) {
        String buildId = request.getBuildId();
        CancellationTokenSource cancellation = buildId.isEmpty() ? null : running.get(buildId);
        if (cancellation != null) {
            cancellation.cancel();
        }
        responseObserver.onNext(CancelResponse.newBuilder()
            .setCancelled(cancellation != null)
            .setMessage(cancellation != null
                ? "Cancellation requested for build " + buildId
                : "No running build with id '" + buildId + "'")
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void queryModel(ModelRequest request, StreamObserver<ModelResponse> responseObserver) {
        // The bridge answers the built-in BuildEnvironment by unpacking the Tooling API's model object
        // onto the wire message. Plugin-contributed Any models are left to the direct (in-daemon) path.
        if (!request.getModelName().isEmpty()) {
            responseObserver.onNext(ModelResponse.newBuilder()
                .setSuccess(false)
                .setError("The bridge does not serve plugin model '" + request.getModelName()
                    + "'; query it against a daemon with the in-daemon gRPC server.")
                .build());
            responseObserver.onCompleted();
            return;
        }

        GradleConnector connector = newConnector(new File(request.getProjectDir()));
        try (ProjectConnection connection = connector.connect()) {
            org.gradle.tooling.model.build.BuildEnvironment env =
                connection.getModel(org.gradle.tooling.model.build.BuildEnvironment.class);
            org.gradle.tooling.internal.grpc.proto.BuildEnvironment.Builder model =
                org.gradle.tooling.internal.grpc.proto.BuildEnvironment.newBuilder()
                    .setGradleVersion(env.getGradle().getGradleVersion());
            File javaHome = env.getJava().getJavaHome();
            if (javaHome != null) {
                model.setJavaHome(javaHome.getAbsolutePath());
            }
            responseObserver.onNext(ModelResponse.newBuilder().setSuccess(true).setBuildEnvironment(model).build());
        } catch (Exception e) {
            responseObserver.onNext(ModelResponse.newBuilder().setSuccess(false).setError(rootMessage(e)).build());
        }
        responseObserver.onCompleted();
    }

    private GradleConnector newConnector(File projectDir) {
        GradleConnector connector = GradleConnector.newConnector().forProjectDirectory(projectDir);
        if (!gradleInstallation.isEmpty()) {
            connector.useInstallation(new File(gradleInstallation));
        } else if (!gradleVersion.isEmpty()) {
            connector.useGradleVersion(gradleVersion);
        }
        return connector;
    }

    private static void emitResult(StreamObserver<BuildEvent> observer, Object lock, boolean success, Outcome outcome, String message, Throwable failure) {
        BuildResult.Builder result = BuildResult.newBuilder()
            .setSuccess(success)
            .setMessage(message)
            .setOutcome(outcome);
        if (failure != null) {
            result.addFailures(toFailure(failure));
        }
        synchronized (lock) {
            observer.onNext(BuildEvent.newBuilder().setResult(result).build());
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

    private static BuildEvent toWireEvent(org.gradle.tooling.events.ProgressEvent event) {
        if (event instanceof org.gradle.tooling.events.problems.SingleProblemEvent) {
            return toProblemEvent((org.gradle.tooling.events.problems.SingleProblemEvent) event);
        }
        org.gradle.tooling.events.OperationDescriptor descriptor = event.getDescriptor();
        if (!(descriptor instanceof org.gradle.tooling.events.task.TaskOperationDescriptor)) {
            return null;
        }
        OperationDescriptor.Builder desc = OperationDescriptor.newBuilder()
            .setId(descriptor.getDisplayName())
            .setParentId(descriptor.getParent() != null ? descriptor.getParent().getDisplayName() : "")
            .setDisplayName(descriptor.getDisplayName())
            .setType(OperationType.OPERATION_TYPE_TASK)
            .setTask(TaskOperationDetails.newBuilder()
                .setTaskPath(((org.gradle.tooling.events.task.TaskOperationDescriptor) descriptor).getTaskPath()));

        if (event instanceof org.gradle.tooling.events.StartEvent) {
            return BuildEvent.newBuilder().setOperationStarted(OperationStarted.newBuilder().setOperation(desc)).build();
        }
        if (event instanceof org.gradle.tooling.events.FinishEvent) {
            org.gradle.tooling.events.OperationResult result = ((org.gradle.tooling.events.FinishEvent) event).getResult();
            OperationResult.Builder outcome = OperationResult.newBuilder().setOutcome(outcomeOf(result));
            if (result instanceof org.gradle.tooling.events.FailureResult) {
                List<? extends org.gradle.tooling.Failure> failures = ((org.gradle.tooling.events.FailureResult) result).getFailures();
                if (!failures.isEmpty() && failures.get(0).getMessage() != null) {
                    outcome.setFailureMessage(failures.get(0).getMessage());
                }
            }
            long duration = result.getEndTime() - result.getStartTime();
            return BuildEvent.newBuilder()
                .setOperationFinished(OperationFinished.newBuilder().setOperation(desc).setResult(outcome).setDurationMillis(duration))
                .build();
        }
        return null;
    }

    private static Outcome outcomeOf(org.gradle.tooling.events.OperationResult result) {
        if (result instanceof org.gradle.tooling.events.task.TaskSuccessResult) {
            org.gradle.tooling.events.task.TaskSuccessResult success = (org.gradle.tooling.events.task.TaskSuccessResult) result;
            if (success.isFromCache()) {
                return Outcome.OUTCOME_FROM_CACHE;
            }
            if (success.isUpToDate()) {
                return Outcome.OUTCOME_UP_TO_DATE;
            }
            return Outcome.OUTCOME_SUCCESS;
        }
        if (result instanceof org.gradle.tooling.events.task.TaskSkippedResult) {
            return Outcome.OUTCOME_SKIPPED;
        }
        if (result instanceof org.gradle.tooling.events.FailureResult) {
            return Outcome.OUTCOME_FAILED;
        }
        if (result instanceof org.gradle.tooling.events.SuccessResult) {
            return Outcome.OUTCOME_SUCCESS;
        }
        return Outcome.OUTCOME_UNSPECIFIED;
    }

    private static BuildEvent toProblemEvent(org.gradle.tooling.events.problems.SingleProblemEvent event) {
        org.gradle.tooling.events.problems.Problem problem = event.getProblem();
        org.gradle.tooling.events.problems.ProblemDefinition definition = problem.getDefinition();
        ProblemEvent.Builder builder = ProblemEvent.newBuilder()
            .setCategory(definition.getId().getDisplayName())
            .setSeverity(severityOf(definition.getSeverity()));
        if (problem.getContextualLabel() != null && problem.getContextualLabel().getContextualLabel() != null) {
            builder.setLabel(problem.getContextualLabel().getContextualLabel());
        }
        if (problem.getDetails() != null && problem.getDetails().getDetails() != null) {
            builder.setDetails(problem.getDetails().getDetails());
        }
        for (org.gradle.tooling.events.problems.Solution solution : problem.getSolutions()) {
            builder.addSolutions(solution.getSolution());
        }
        for (org.gradle.tooling.events.problems.Location location : problem.getOriginLocations()) {
            builder.addLocations(location.toString());
        }
        if (definition.getDocumentationLink() != null && definition.getDocumentationLink().getUrl() != null) {
            builder.setDocumentationLink(definition.getDocumentationLink().getUrl());
        }
        return BuildEvent.newBuilder().setProblem(builder).build();
    }

    private static Severity severityOf(org.gradle.tooling.events.problems.Severity severity) {
        switch (severity.getSeverity()) {
            case 0:
                return Severity.SEVERITY_ADVICE;
            case 1:
                return Severity.SEVERITY_WARNING;
            case 2:
                return Severity.SEVERITY_ERROR;
            default:
                return Severity.SEVERITY_UNSPECIFIED;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    /**
     * An {@link OutputStream} that turns the Tooling API's build output into one {@code OutputLine}
     * event per line, forwarded on the (serialized) response stream.
     */
    private static final class LineStream extends OutputStream {
        private final StreamObserver<BuildEvent> observer;
        private final Object lock;
        private final LogLevel level;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        LineStream(StreamObserver<BuildEvent> observer, Object lock, LogLevel level) {
            this.observer = observer;
            this.lock = lock;
            this.level = level;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushLine();
            } else {
                buffer.write(b);
            }
        }

        @Override
        public void flush() {
            if (buffer.size() > 0) {
                flushLine();
            }
        }

        private void flushLine() {
            String text = new String(buffer.toByteArray());
            buffer.reset();
            synchronized (lock) {
                observer.onNext(BuildEvent.newBuilder()
                    .setOutput(OutputLine.newBuilder().setText(text).setLevel(level).build())
                    .build());
            }
        }
    }
}
