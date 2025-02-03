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

import org.gradle.internal.build.event.types.DefaultFailure;
import org.gradle.internal.build.event.types.DefaultFileDownloadDescriptor;
import org.gradle.internal.build.event.types.DefaultFileDownloadFailureResult;
import org.gradle.internal.build.event.types.DefaultFileDownloadSuccessResult;
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent;
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent;
import org.gradle.internal.build.event.types.DefaultStatusEvent;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.progress.ResourceOperation;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.gradle.internal.util.NumberUtil.formatBytes;

/**
 * Class responsible for listening to the download of a toolchain for starting the Gradle daemon.
 * <p>
 * The listener cannot be reused as it is stateful.
 */
public class ToolchainDownloadProgressListener implements DownloadProgressListener {

    private final ProgressLogger progressLogger;
    private final Optional<InternalBuildProgressListener> buildProgressListener;
    private final BuildOperationIdFactory operationIdFactory;
    @Nullable
    private DefaultFileDownloadDescriptor defaultFileDownloadDescriptor;

    public ToolchainDownloadProgressListener(ProgressLoggerFactory progressLoggerFactory, Optional<InternalBuildProgressListener> buildProgressListener, BuildOperationIdFactory operationIdFactory) {
        this.progressLogger = progressLoggerFactory.newOperation(ToolchainDownloadProgressListener.class);
        this.buildProgressListener = buildProgressListener;
        this.operationIdFactory = operationIdFactory;
    }

    @Override
    public void downloadStarted(URI uri, long contentLengthBytes, long startTime) {
        progressLogger.start("Downloading toolchain from URI " + uri, null);

        if (buildProgressListener.isPresent()) {
            String displayName = getDisplayName(uri);
            assert defaultFileDownloadDescriptor == null;
            defaultFileDownloadDescriptor = new DefaultFileDownloadDescriptor(new OperationIdentifier(operationIdFactory.nextId()), displayName, displayName + " as a JVM for starting the Gradle daemon", null, uri);
            DefaultOperationStartedProgressEvent startEvent = new DefaultOperationStartedProgressEvent(startTime, defaultFileDownloadDescriptor);
            buildProgressListener.get().onEvent(startEvent);
        }
    }

    @Override
    public void downloadStatusChanged(URI uri, long downloadedBytes, long contentLengthBytes, long eventTime) {
        String downloadProgressMessage = String.format("Downloading toolchain from URI %s > %s/%s %sed", uri, formatBytes(downloadedBytes), formatBytes(contentLengthBytes), ResourceOperation.Type.download);
        progressLogger.progress(downloadProgressMessage);

        buildProgressListener.ifPresent(listener -> {
            listener.onEvent(new DefaultStatusEvent(eventTime, defaultFileDownloadDescriptor, downloadedBytes, contentLengthBytes, "bytes"));
        });
    }

    @Override
    public void downloadFinished(URI uri, long downloadedBytes, long startTime, long finishTime) {
        progressLogger.completed("Downloaded toolchain " + uri, false);

        buildProgressListener.ifPresent(listener -> {
            DefaultFileDownloadSuccessResult result = new DefaultFileDownloadSuccessResult(startTime, finishTime, downloadedBytes);
            listener.onEvent(new DefaultOperationFinishedProgressEvent(finishTime, defaultFileDownloadDescriptor, result));
        });
    }

    @Override
    public void downloadFailed(URI uri, Exception exception, long downloadedBytes, long startTime, long finishTime) {
        progressLogger.completed("Failed to download toolchain " + uri, true);

        buildProgressListener.ifPresent(listener -> {
            DefaultFileDownloadFailureResult result = new DefaultFileDownloadFailureResult(startTime, finishTime, singletonList(DefaultFailure.fromThrowable(exception)), downloadedBytes);
            listener.onEvent(new DefaultOperationFinishedProgressEvent(finishTime, defaultFileDownloadDescriptor, result));
        });
    }

    private String getDisplayName(URI uri) {
        return String.format("Download %s", uri);
    }
}
