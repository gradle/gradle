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

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.gradle.internal.resource.transfer.ProgressLoggingInputStream;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;

public class ToolchainExternalResourceAccessor extends HttpResourceAccessor {

    private final DownloadProgressListener downloadProgressListener;
    private final Clock clock;

    public ToolchainExternalResourceAccessor(HttpClientHelper httpClientHelper, Clock clock, DownloadProgressListener downloadProgressListener) {
        super(httpClientHelper);
        this.clock = clock;
        this.downloadProgressListener = downloadProgressListener;
    }

    @Nullable
    @Override
    public <T> T withContent(ExternalResourceName location, boolean revalidate, ExternalResource.ContentAndMetadataAction<T> action) throws ResourceException {
        ExternalResourceReadResponse response = openResource(location, revalidate);
        if (response == null) {
            return null;
        }

        long startTime = clock.getCurrentTime();
        AtomicLong downloadedBytes = new AtomicLong(0);
        try (InputStream inputStream = response.openStream(); ExternalResourceReadResponse responseCloser = response) {
            ProgressLoggingInputStream progressLoggingInputStream = new ProgressLoggingInputStream(inputStream, processedBytes -> {
                if (downloadedBytes.get() == 0) {
                    downloadProgressListener.downloadStarted(location.getUri(), response.getMetaData().getContentLength(), startTime);
                }
                downloadedBytes.addAndGet(processedBytes);
                downloadProgressListener.downloadStatusChanged(location.getUri(), downloadedBytes.get(), response.getMetaData().getContentLength(), clock.getCurrentTime());

                if (downloadedBytes.get() == response.getMetaData().getContentLength()) {
                    downloadProgressListener.downloadFinished(location.getUri(), downloadedBytes.get(), startTime, clock.getCurrentTime());
                }
            });
            return action.execute(progressLoggingInputStream, responseCloser.getMetaData());
        } catch (Exception e) {
            downloadProgressListener.downloadFailed(location.getUri(), e, downloadedBytes.get(), startTime, clock.getCurrentTime());
            throw ResourceExceptions.getFailed(location.getUri(), e);
        }
    }
}
