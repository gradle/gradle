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
import org.gradle.internal.time.Clock;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.FileDownloadResult;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult;
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
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

public class DistributionInstaller {
    private static final String APP_NAME = "Gradle Tooling API";
    private static final InternalBuildProgressListener NO_OP = new NoOpListener();
    private final ProgressLoggerFactory progressLoggerFactory;
    private final InternalBuildProgressListener buildProgressListener;
    private final Clock clock;
    private final AtomicReference<InternalBuildProgressListener> currentListener = new AtomicReference<InternalBuildProgressListener>(NO_OP);
    // Protects the following state
    private final Object lock = new Object();
    private boolean completed;
    private boolean cancelled;
    private Throwable failure;

    public DistributionInstaller(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener buildProgressListener, Clock clock) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildProgressListener = getListener(buildProgressListener);
        this.clock = clock;
    }

    private InternalBuildProgressListener getListener(InternalBuildProgressListener buildProgressListener) {
        if (buildProgressListener.getSubscribedOperations().contains(InternalBuildProgressListener.FILE_DOWNLOAD)) {
            return buildProgressListener;
        } else {
            return NO_OP;
        }
    }

    /**
     * Installs the distribution and returns the result.
     */
    public File install(File userHomeDir, File projectDir, WrapperConfiguration wrapperConfiguration, Map<String, String> systemProperties) throws Exception {
        Install install = new Install(new Logger(false), new AsyncDownload(systemProperties), new PathAssembler(userHomeDir, projectDir));
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
        private long downloaded = 0;

        ForwardingDownloadProgressListener(OperationDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
            this.downloaded = downloaded;
            StatusEvent statusEvent = new DefaultStatusEvent(clock.getCurrentTime(), descriptor, contentLength, downloaded, "bytes");
            // This is called from the download thread. Only forward the events when not cancelled
            currentListener.get().onEvent(statusEvent);
        }
    }

    private class AsyncDownload implements IDownload {
        private final Map<String, String> systemProperties;

        public AsyncDownload(Map<String, String> systemProperties) {
            this.systemProperties = systemProperties;
        }

        @Override
        public void download(URI address, File destination) throws Exception {
            synchronized (lock) {
                doDownload(address, destination);
            }
        }

        private void doDownload(URI address, File destination) throws Exception {
            String displayName = "Download " + address;
            FileDownloadOperationDescriptor descriptor = new DefaultFileDownloadOperationDescriptor(displayName, address, null);
            long startTime = clock.getCurrentTime();
            buildProgressListener.onEvent(new DefaultFileDownloadStartEvent(startTime, displayName + " started", descriptor));

            Throwable failure = null;
            long bytesDownloaded = 0;
            try {
                bytesDownloaded = withProgressLogging(address, destination, descriptor);
            } catch (Throwable t) {
                failure = t;
            }

            long endTime = clock.getCurrentTime();
            FileDownloadResult result = failure == null ? new DefaultFileDownloadSuccessResult(startTime, endTime, bytesDownloaded) : new DefaultFileDownloadFailureResult(startTime, endTime, Collections.singletonList(DefaultFailure.fromThrowable(failure)), bytesDownloaded);
            buildProgressListener.onEvent(new DefaultFileDownloadFinishEvent(endTime, displayName + " finished", descriptor, result));
            if (failure != null) {
                if (failure instanceof Exception) {
                    throw (Exception) failure;
                }
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }

        private long withProgressLogging(URI address, File destination, OperationDescriptor operationDescriptor) throws Throwable {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DistributionInstaller.class);
            progressLogger.setDescription("Download " + address);
            progressLogger.started();
            try {
                return withAsyncDownload(address, destination, operationDescriptor);
            } finally {
                progressLogger.completed();
            }
        }

        private long withAsyncDownload(final URI address, final File destination, final OperationDescriptor operationDescriptor) throws Throwable {
            final ForwardingDownloadProgressListener listener = new ForwardingDownloadProgressListener(operationDescriptor);
            currentListener.set(buildProgressListener);
            try {
                // Start the download in another thread and wait for the result
                Thread thread = new Thread("Distribution download") {
                    @Override
                    public void run() {
                        try {
                            new Download(new Logger(false), listener, APP_NAME, GradleVersion.current().getVersion(), systemProperties).download(address, destination);
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
            return listener.downloaded;
        }
    }
}
