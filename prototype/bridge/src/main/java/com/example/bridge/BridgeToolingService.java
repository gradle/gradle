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
import org.gradle.tooling.internal.grpc.proto.BuildEvent;
import org.gradle.tooling.internal.grpc.proto.BuildRequest;
import org.gradle.tooling.internal.grpc.proto.BuildResult;
import org.gradle.tooling.internal.grpc.proto.CancelRequest;
import org.gradle.tooling.internal.grpc.proto.CancelResponse;
import org.gradle.tooling.internal.grpc.proto.LogLevel;
import org.gradle.tooling.internal.grpc.proto.ModelRequest;
import org.gradle.tooling.internal.grpc.proto.ModelResponse;
import org.gradle.tooling.internal.grpc.proto.OutputLine;
import org.gradle.tooling.internal.grpc.proto.ProgressEvent;
import org.gradle.tooling.internal.grpc.proto.ProgressType;
import org.gradle.tooling.internal.grpc.proto.ToolingGrpc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
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

    public BridgeToolingService(String gradleVersion, String gradleInstallation) {
        this.gradleVersion = gradleVersion;
        this.gradleInstallation = gradleInstallation;
    }

    @Override
    public void runBuild(BuildRequest request, StreamObserver<BuildEvent> responseObserver) {
        // gRPC requires calls onto the observer to be serialized; the Tooling API writes output from
        // its own threads, so guard every onNext/onCompleted with this monitor.
        Object lock = new Object();
        String buildId = request.getBuildId();
        CancellationTokenSource cancellation = GradleConnector.newCancellationTokenSource();
        if (!buildId.isEmpty()) {
            running.put(buildId, cancellation);
        }

        GradleConnector connector = newConnector(new File(request.getProjectDir()));
        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher launcher = connection.newBuild().withCancellationToken(cancellation.token());

            List<String> tasks = new ArrayList<>();
            List<String> arguments = new ArrayList<>();
            for (String arg : request.getArgsList()) {
                (arg.startsWith("-") ? arguments : tasks).add(arg);
            }
            if (!tasks.isEmpty()) {
                launcher.forTasks(tasks.toArray(new String[0]));
            }
            if (!arguments.isEmpty()) {
                launcher.withArguments(arguments);
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

            launcher.run();
            emitResult(responseObserver, lock, true, "BUILD SUCCESSFUL");
        } catch (BuildCancelledException e) {
            emitResult(responseObserver, lock, false, "BUILD CANCELLED");
        } catch (Exception e) {
            emitResult(responseObserver, lock, false, "BUILD FAILED: " + rootMessage(e));
        } finally {
            if (!buildId.isEmpty()) {
                running.remove(buildId);
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

    private static void emitResult(StreamObserver<BuildEvent> observer, Object lock, boolean success, String message) {
        synchronized (lock) {
            observer.onNext(BuildEvent.newBuilder()
                .setResult(BuildResult.newBuilder().setSuccess(success).setMessage(message).build())
                .build());
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
