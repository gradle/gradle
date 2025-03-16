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
package org.gradle.launcher.daemon.toolchain;

import java.net.URI;

public interface DownloadProgressListener {

    void downloadStarted(URI uri, long contentLengthBytes, long startTime);

    void downloadStatusChanged(URI uri, long downloadedBytes, long contentLengthBytes, long eventTime);

    void downloadFinished(URI uri, long downloadedBytes, long startTime, long finishTime);

    void downloadFailed(URI uri, Exception exception, long downloadedBytes, long startTime, long finishTime);
}
