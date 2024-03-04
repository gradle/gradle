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
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.gradle.internal.resource.transfer.ProgressLoggingInputStream;
import org.gradle.internal.resource.transfer.ResourceOperation;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpResourceAccessor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.util.NumberUtil.formatBytes;

public class ToolchainExternalResourceAccessor extends HttpResourceAccessor {

    private final ProgressLoggerFactory progressLoggerFactory;

    public ToolchainExternalResourceAccessor(HttpClientHelper httpClientHelper, ProgressLoggerFactory progressLoggerFactory) {
        super(httpClientHelper);
        this.progressLoggerFactory = progressLoggerFactory;
    }

    @Nullable
    @Override
    public <T> T withContent(ExternalResourceName location, boolean revalidate, ExternalResource.ContentAndMetadataAction<T> action) throws ResourceException {
        ExternalResourceReadResponse response = openResource(location, revalidate);
        if (response == null) {
            return null;
        }

        AtomicLong downloadedBytes = new AtomicLong();
        try (InputStream inputStream = response.openStream(); ExternalResourceReadResponse responseCloser = response) {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(JavaInstallationRegistry.class).start("Downloading toolchain from URI " + location.getUri(), null);
            ProgressLoggingInputStream progressLoggingInputStream = new ProgressLoggingInputStream(inputStream, processedBytes -> {
                downloadedBytes.addAndGet(processedBytes);
                progressLogger.progress(getProgressString(location.getUri(), downloadedBytes.get(), responseCloser.getMetaData().getContentLength()));
            });
            return action.execute(progressLoggingInputStream, responseCloser.getMetaData());
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(location.getUri(), e);
        }
    }

    public String getProgressString(URI uri, long currentLength, long totalLength) {
        return String.format("Downloading toolchain from URI %s > %s/%s %sed", uri, formatBytes(currentLength), formatBytes(totalLength), ResourceOperation.Type.download);
    }
}
