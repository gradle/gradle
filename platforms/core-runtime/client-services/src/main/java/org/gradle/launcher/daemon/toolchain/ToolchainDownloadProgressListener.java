/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.toolchain;

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.resource.transfer.ResourceOperation;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.FileDownloadResult;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadOperationDescriptor;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent;
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult;
import org.gradle.tooling.events.internal.DefaultStatusEvent;
import org.gradle.tooling.internal.consumer.DefaultFailure;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import static org.gradle.internal.util.NumberUtil.formatBytes;

public class ToolchainDownloadProgressListener implements DownloadProgressListener {

    private final ProgressLogger progressLogger;
    private final Optional<InternalBuildProgressListener> buildProgressListener;

    public ToolchainDownloadProgressListener(ProgressLoggerFactory progressLoggerFactory, Optional<InternalBuildProgressListener> buildProgressListener) {
        this.progressLogger = progressLoggerFactory.newOperation(ToolchainDownloadProgressListener.class);
        this.buildProgressListener = buildProgressListener;
    }

    @Override
    public void downloadStarted(URI uri, long contentLengthBytes, long startTime) {
        progressLogger.start("Downloading toolchain from URI " + uri, null);

        if (buildProgressListener.isPresent()) {
            String displayName = getDisplayName(uri);
            FileDownloadOperationDescriptor descriptor = new DefaultFileDownloadOperationDescriptor(displayName, uri, null);
            ProgressEvent startEvent = new DefaultFileDownloadStartEvent(startTime, displayName + " started", descriptor);
            buildProgressListener.get().onEvent(startEvent);
        }
    }

    @Override
    public void downloadStatusChanged(URI uri, long downloadedBytes, long contentLengthBytes, long eventTime) {
        String downloadProgressMessage = String.format("Downloading toolchain from URI %s > %s/%s %sed", uri, formatBytes(downloadedBytes), formatBytes(contentLengthBytes), ResourceOperation.Type.download);
        progressLogger.progress(downloadProgressMessage);

        if (buildProgressListener.isPresent()) {
            String displayName = getDisplayName(uri);
            FileDownloadOperationDescriptor descriptor = new DefaultFileDownloadOperationDescriptor(displayName, uri, null);
            StatusEvent statusEvent = new DefaultStatusEvent(eventTime, descriptor, contentLengthBytes, downloadedBytes, "bytes");
            buildProgressListener.get().onEvent(statusEvent);
        }
    }

    @Override
    public void downloadFinished(URI uri, long downloadedBytes, long startTime, long finishTime) {
        progressLogger.completed("Downloaded toolchain " + uri, false);

        if (buildProgressListener.isPresent()) {
            String displayName = getDisplayName(uri);
            FileDownloadOperationDescriptor descriptor = new DefaultFileDownloadOperationDescriptor(displayName, uri, null);
            FileDownloadResult result = new DefaultFileDownloadSuccessResult(startTime, finishTime, downloadedBytes);
            ProgressEvent finishEvent = new DefaultFileDownloadFinishEvent(finishTime, displayName + " finished", descriptor, result);
            buildProgressListener.get().onEvent(finishEvent);
        }
    }

    @Override
    public void downloadFailed(URI uri, Exception exception, long downloadedBytes, long startTime, long finishTime) {
        progressLogger.completed("Failed to download toolchain " + uri, true);

        if (buildProgressListener.isPresent()) {
            String displayName = getDisplayName(uri);
            FileDownloadOperationDescriptor descriptor = new DefaultFileDownloadOperationDescriptor(displayName, uri, null);
            FileDownloadResult result = new DefaultFileDownloadFailureResult(startTime, finishTime, Collections.singletonList(DefaultFailure.fromThrowable(exception)), downloadedBytes);
            ProgressEvent finishEvent = new DefaultFileDownloadFinishEvent(finishTime, displayName + " failed", descriptor, result);
            buildProgressListener.get().onEvent(finishEvent);
        }
    }

    private String getDisplayName(URI uri) {
        return String.format("Download %s", uri);
    }
}
