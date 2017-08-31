/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.internal.ConsumerOperationDescriptor;
import org.gradle.tooling.events.internal.DefaultFinishEvent;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;
import org.gradle.tooling.events.internal.DefaultStartEvent;
import org.gradle.tooling.events.internal.DefaultStatusEvent;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.Download;
import org.gradle.wrapper.DownloadProgressListener;
import org.gradle.wrapper.IDownload;
import org.gradle.wrapper.Install;
import org.gradle.wrapper.Logger;
import org.gradle.wrapper.PathAssembler;
import org.gradle.wrapper.WrapperConfiguration;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

public class DistributionInstaller {
    private static final String APP_NAME = "Gradle Tooling API";
    private static final InternalBuildProgressListener NO_OP = new NoOpListener();
    private final ProgressLoggerFactory progressLoggerFactory;
    private final InternalBuildProgressListener buildProgressListener;
    private final TimeProvider timeProvider;
    private final AtomicReference<InternalBuildProgressListener> currentListener = new AtomicReference<InternalBuildProgressListener>(NO_OP);
    // Protects the following state
    private final Object lock = new Object();
    private boolean completed;
    private boolean cancelled;
    private Throwable failure;

    public DistributionInstaller(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener buildProgressListener, TimeProvider timeProvider) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildProgressListener = buildProgressListener;
        this.timeProvider = timeProvider;
    }

    /**
     * Installs the distribution and returns the result.
     */
    public File install(File userHomeDir, WrapperConfiguration wrapperConfiguration) throws Exception {
        Install install = new Install(new Logger(false), new AsyncDownload(), new PathAssembler(userHomeDir));
        return install.createDist(wrapperConfiguration);
    }

    /**
     * Cancels the current installation, if running.
     */
    public void cancel() {
        synchronized (lock) {
            cancelled = true;
            lock.notifyAll();
        }
    }

    private void doDownload(URI address, File destination) throws Exception {
        String displayName = "Download " + address;
        OperationDescriptor descriptor = new ConsumerOperationDescriptor(displayName);
        long startTime = timeProvider.getCurrentTime();
        buildProgressListener.onEvent(new DefaultStartEvent(startTime, displayName + " started", descriptor));

        Throwable failure = null;
        try {
            withProgressLogging(address, destination, descriptor);
        } catch (Throwable t) {
            failure = t;
        }

        long endTime = timeProvider.getCurrentTime();
        OperationResult result = failure == null ? new DefaultOperationSuccessResult(startTime, endTime) : new DefaultOperationFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)));
        buildProgressListener.onEvent(new DefaultFinishEvent(endTime, displayName + " finished", descriptor, result));
        if (failure != null) {
            if (failure instanceof Exception) {
                throw (Exception) failure;
            }
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    private void withProgressLogging(URI address, File destination, OperationDescriptor operationDescriptor) throws Throwable {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionInstaller.class);
        progressLogger.setDescription("Download " + address);
        progressLogger.started();
        try {
            withAsyncDownload(address, destination, operationDescriptor);
        } finally {
            progressLogger.completed();
        }
    }

    private void withAsyncDownload(final URI address, final File destination, final OperationDescriptor operationDescriptor) throws Throwable {
        currentListener.set(buildProgressListener);
        try {
            // Start the download in another thread and wait for the result
            Thread thread = new Thread("Distribution download") {
                @Override
                public void run() {
                    try {
                        new Download(new Logger(false), new ForwardingDownloadProgressListener(operationDescriptor), APP_NAME, GradleVersion.current().getVersion()).download(address, destination);
                    } catch (Throwable t) {
                        synchronized (lock) {
                            failure = t;
                        }
                    } finally {
                        synchronized (lock) {
                            completed = true;
                            lock.notifyAll();
                        }
                    }
                }
            };
            thread.setDaemon(true);
            thread.start();
            synchronized (lock) {
                while (!completed && !cancelled) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                if (failure != null) {
                    throw failure;
                }
                if (cancelled) {
                    // When cancelled, try to stop the download thread but don't attempt to wait for it to complete
                    // Could possibly loop here for a while trying to force the thread to exit
                    thread.interrupt();
                    throw new CancellationException();
                }
            }
        } finally {
            // The download thread may still be running. Ignore any further status events from it
            currentListener.set(NO_OP);
        }
    }

    private static class NoOpListener implements InternalBuildProgressListener {
        @Override
        public void onEvent(Object event) {
        }

        @Override
        public List<String> getSubscribedOperations() {
            return Collections.emptyList();
        }
    }

    private class ForwardingDownloadProgressListener implements DownloadProgressListener {
        private final OperationDescriptor descriptor;

        ForwardingDownloadProgressListener(OperationDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
            String eventDisplayName = descriptor.getDisplayName() + " " + downloaded + "/" + contentLength + " bytes downloaded";
            StatusEvent statusEvent = new DefaultStatusEvent(timeProvider.getCurrentTime(), eventDisplayName, descriptor, contentLength, downloaded, "bytes");
            // This is called from the download thread. Only forward the events when not cancelled
            currentListener.get().onEvent(statusEvent);
        }
    }

    private class AsyncDownload implements IDownload {
        @Override
        public void download(URI address, File destination) throws Exception {
            synchronized (lock) {
                doDownload(address, destination);
            }
        }
    }
}
