/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.events.download.internal;

import org.gradle.tooling.events.download.FileDownloadResult;
import org.gradle.tooling.events.internal.DefaultOperationSuccessResult;

public class DefaultFileDownloadSuccessResult extends DefaultOperationSuccessResult implements FileDownloadResult {
    private final long bytesDownloaded;

    public DefaultFileDownloadSuccessResult(long startTime, long endTime, long bytesDownloaded) {
        super(startTime, endTime);
        this.bytesDownloaded = bytesDownloaded;
    }

    @Override
    public long getBytesDownloaded() {
        return bytesDownloaded;
    }
}
