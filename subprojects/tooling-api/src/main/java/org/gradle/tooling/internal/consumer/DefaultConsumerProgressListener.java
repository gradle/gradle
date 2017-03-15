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

import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.time.TrueTimeProvider;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.StatusEvent;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.internal.DefaultStatusEvent;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.Download;
import org.gradle.wrapper.Logger;

import java.io.File;
import java.net.URI;

public class DefaultConsumerProgressListener implements ConsumerProgressListener {
    private static final String APP_NAME = "Gradle Tooling API";
    private final ProgressLoggerFactory progressLoggerFactory;
    private final InternalBuildProgressListener buildProgressListener;
    private final TimeProvider timeProvider;

    public DefaultConsumerProgressListener(ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener buildProgressListener) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildProgressListener = buildProgressListener;
        this.timeProvider = new TrueTimeProvider();
    }

    @Override
    public void download(URI address, File destination) throws Exception {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultConsumerProgressListener.class);
        progressLogger.setDescription("Download " + address);
        progressLogger.started();
        try {
            new Download(new Logger(false), this, APP_NAME, GradleVersion.current().getVersion()).download(address, destination);
        } finally {
            progressLogger.completed();
        }
    }

    @Override
    public void downloadStatusChanged(URI address, long contentLength, long downloaded) {
        String displayName = "Downloading " + address;
        OperationIdentifier id = new OperationIdentifier(0);
        org.gradle.tooling.internal.provider.events.DefaultOperationDescriptor internalDescriptor =
            new org.gradle.tooling.internal.provider.events.DefaultOperationDescriptor(id, displayName, displayName, null);
        OperationDescriptor descriptor = new DefaultOperationDescriptor(internalDescriptor, null);
        StatusEvent statusEvent = new DefaultStatusEvent(timeProvider.getCurrentTime(), displayName, descriptor, contentLength, downloaded, "bytes");
        buildProgressListener.onEvent(statusEvent);
    }
}
