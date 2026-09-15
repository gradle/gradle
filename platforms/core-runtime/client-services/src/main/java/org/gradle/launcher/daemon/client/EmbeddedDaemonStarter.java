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
package org.gradle.launcher.daemon.client;

import org.gradle.api.GradleException;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.exception.InitializationException;
import org.gradle.internal.IoActions;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.startup.DaemonStartupCommunication;
import org.gradle.launcher.daemon.startup.DaemonStartupInfo;
import org.gradle.launcher.daemon.startup.DefaultDaemonServerConfiguration;
import org.gradle.process.internal.CurrentProcess;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link DaemonStarter} that starts a daemon embedded in this process.
 * <p>
 * The daemon server classpath is loaded into a classloader that is a child of the client's,
 * and the well-known entry point is instantiated and invoked reflectively. The client has no
 * compile-time dependency on the daemon. The startup protocol is identical to a forked daemon's,
 * replayed over in-memory streams instead of process IO. The serialized configuration is
 * written to the daemon's stdin, and the startup handshake is read from its stdout. After startup,
 * the daemon is indistinguishable from a separate-process daemon. All communication happens over
 * its socket connection.
 */
@NullMarked
public class EmbeddedDaemonStarter implements DaemonStarter {

    private static final Logger LOGGER = Logging.getLogger(EmbeddedDaemonStarter.class);

    /**
     * The well-known class name of the embedded daemon's main class to load
     * from the daemon classpath.
     */
    private static final String EMBEDDED_DAEMON_MAIN_CLASS = "org.gradle.launcher.daemon.bootstrap.EmbeddedDaemonMain";

    private final DaemonDir daemonDir;
    private final DaemonParameters daemonParameters;
    private final FileCollectionFactory fileCollectionFactory;

    public EmbeddedDaemonStarter(
        DaemonDir daemonDir,
        DaemonParameters daemonParameters,
        FileCollectionFactory fileCollectionFactory
    ) {
        this.daemonDir = daemonDir;
        this.daemonParameters = daemonParameters;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public DaemonHandle startDaemon(boolean singleUse) {
        String daemonUid = UUID.randomUUID().toString();

        // The embedded daemon cannot be 'told' its startup options since it runs in this process.
        // So we infer the JVM options from the current process.
        List<String> jvmOptions = new CurrentProcess(fileCollectionFactory).getJvmOptions().getAllImmutableJvmArgs();

        DefaultDaemonServerConfiguration configuration = new DefaultDaemonServerConfiguration(
            daemonParameters.getGradleUserHomeDir(),
            daemonUid,
            daemonDir.getBaseDir(),
            daemonParameters.getIdleTimeout(),
            daemonParameters.getPeriodicCheckInterval(),
            singleUse,
            daemonParameters.getPriority(),
            jvmOptions,
            daemonParameters.shouldApplyInstrumentationAgent(),
            daemonParameters.getNativeServicesMode()
        );

        // Write the client-side boostrap handshake message.
        StreamByteBuffer stdinBuffer = new StreamByteBuffer();
        DaemonStartupCommunication.writeDaemonServerConfiguration(stdinBuffer.getOutputStream(), configuration);
        InputStream stdin = stdinBuffer.getInputStream();

        ClassLoader daemonClassLoader = createDaemonClassLoader();

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            PipedInputStream handshakeInput = new PipedInputStream();
            PipedOutputStream stdout = new PipedOutputStream(handshakeInput);

            Future<?> daemonCompletion = startDaemon(daemonClassLoader, stdin, stdout, stderr);

            try {
                // TODO: The reads from the daemon output should have a timeout. We have a similar
                //  problem with forked daemons where a daemon that hangs before writing the handshake
                //  (without exiting/closing its stdout) hangs the client indefinitely.
                DaemonStartupInfo startupInfo = DaemonStartupCommunication.readStartupInfoFromDaemonOutput(handshakeInput);
                return new EmbeddedDaemonHandle(startupInfo, daemonCompletion);
            } catch (InitializationException e) {
                // The daemon's output ended without a handshake. Prefer the daemon's own failure
                // as the cause when it stopped with one.
                Throwable daemonFailure = waitFor(daemonCompletion, 30, TimeUnit.SECONDS);
                throw daemonStartupFailure(stderr, daemonFailure != null ? daemonFailure : e);
            }
        } catch (IOException e) {
            throw daemonStartupFailure(stderr, e);
        }
    }

    /**
     * Creates the daemon classloader as a child of the client classloader, specifically so
     * we can share the existing NativeServices singleton the client has already initialized. JNI
     * libraries can only be loaded once per JVM. Since the client already loads the native
     * services, we must share their instance.
     */
    private ClassLoader createDaemonClassLoader() {
        // TODO: Eventually, we may want to install a filter between the client and embedded daemon
        // classloader, but for now this should be okay.
        ModuleRegistry registry = new DefaultModuleRegistry(CurrentGradleInstallation.get());
        ClassPath classpath = registry.getModule("gradle-daemon-server").getImplementationClasspath();
        return VisitableURLClassLoader.fromClassPath("gradle-embedded-daemon-loader", getClass().getClassLoader(), classpath);
    }

    /**
     * Starts the embedded daemon on its own thread. The thread is the embedded daemon's
     * "process" and runs until the daemon expires. The returned future completes when the
     * daemon stops, carrying any failure it stopped with.
     */
    private static Future<?> startDaemon(ClassLoader daemonClassLoader, InputStream stdin, OutputStream stdout, OutputStream stderr) {
        Object daemonMain;
        Method runMethod;
        try {
            Class<?> daemonMainClass = daemonClassLoader.loadClass(EMBEDDED_DAEMON_MAIN_CLASS);
            daemonMain = daemonMainClass.getConstructor().newInstance();
            runMethod = daemonMainClass.getMethod("run", InputStream.class, OutputStream.class, OutputStream.class);
        } catch (ReflectiveOperationException e) {
            throw new GradleException("Could not load the embedded daemon entry point.", e);
        }

        FutureTask<?> daemonCompletion = new FutureTask<>(() -> {
            try {
                runMethod.invoke(daemonMain, stdin, stdout, stderr);
                return null;
            } catch (InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e.getCause());
            } finally {
                // EmbeddedDaemonMain closes its streams itself. This guards against failures
                // to invoke it at all, so a client blocked reading the handshake unblocks.
                IoActions.closeQuietly(stdout);
            }
        });
        Thread daemonThread = new Thread(daemonCompletion, "Gradle embedded daemon");
        daemonThread.setContextClassLoader(daemonClassLoader);
        daemonThread.start();
        return daemonCompletion;
    }

    /**
     * Waits for the daemon to stop, returning the failure it stopped with, if any.
     * <p>
     * Returns null when the daemon stopped cleanly or when it has not stopped within the
     * timeout. Use {@link Future#isDone()} afterward to distinguish the two.
     */
    private static @Nullable Throwable waitFor(Future<?> daemonCompletion, long timeout, TimeUnit unit) {
        try {
            daemonCompletion.get(timeout, unit);
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException ignored) {
            // The daemon has not stopped.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private static GradleException daemonStartupFailure(ByteArrayOutputStream stderr, Throwable failure) {
        String errorOutput = new String(stderr.toByteArray(), StandardCharsets.UTF_8);
        return new GradleException(
            "Could not start the embedded Gradle daemon." +
                (errorOutput.isEmpty() ? "" : "\nDaemon error output:\n" + errorOutput),
            failure
        );
    }

    private static class EmbeddedDaemonHandle implements DaemonHandle {

        private final DaemonStartupInfo startupInfo;
        private final Future<?> daemonCompletion;

        public EmbeddedDaemonHandle(DaemonStartupInfo startupInfo, Future<?> daemonCompletion) {
            this.startupInfo = startupInfo;
            this.daemonCompletion = daemonCompletion;
        }

        @Override
        public DaemonStartupInfo getStartupInfo() {
            return startupInfo;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            Throwable daemonFailure = waitFor(daemonCompletion, timeout, unit);
            if (daemonFailure != null) {
                // A daemon that stopped with a failure has still terminated.
                LOGGER.warn("The embedded daemon stopped with a failure.", daemonFailure);
            }
            return daemonCompletion.isDone();
        }

    }

}
