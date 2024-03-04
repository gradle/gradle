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

package org.gradle.launcher.daemon.toolchain


import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFailureResult
import org.gradle.tooling.events.download.internal.DefaultFileDownloadFinishEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadStartEvent
import org.gradle.tooling.events.download.internal.DefaultFileDownloadSuccessResult
import org.gradle.tooling.events.internal.DefaultStatusEvent
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import spock.lang.Specification

class ToolchainDownloadProgressListenerTest extends Specification {

    ToolchainDownloadProgressListener progressListener
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def progressLogger = Mock(ProgressLogger)
    def buildProgressListener = Mock(InternalBuildProgressListener)
    def uri = new URI("https://server.com/toolchain.zip")

    def setup() {
        progressLogger.start(_ as String, null) >> progressLogger
        progressLoggerFactory.newOperation(_ as Class<ToolchainDownloadProgressListener>) >> progressLogger
        progressListener = new ToolchainDownloadProgressListener(progressLoggerFactory, Optional.of(buildProgressListener))
    }

    def "download started event"() {
        when:
        progressListener.downloadStarted(uri, 100, 100)

        then:
        1 * progressLogger.start("Downloading toolchain from URI $uri", null)
        1 * buildProgressListener.onEvent({ DefaultFileDownloadStartEvent event ->
            event.eventTime == 100
            event.displayName == "Download $uri started"
            event.descriptor.name == "Download $uri"
            event.descriptor.uri == uri
        })
    }

    def "download status changed event"() {
        when:
        progressListener.downloadStatusChanged(uri, 1024, 4096, 100)

        then:
        1 * progressLogger.progress("Downloading toolchain from URI $uri > 1 KiB/4 KiB downloaded")
        1 * buildProgressListener.onEvent({ DefaultStatusEvent event ->
            event.progress == 1024
            event.total == 4096
            event.unit == "bytes"
            event.displayName == "Download $uri 1024/4096 bytes completed"
            event.descriptor.name == "Download $uri"
            event.descriptor.uri == uri
        })
    }

    def "download finished event"() {
        when:
        progressListener.downloadFinished(uri, 4096, 100, 101)

        then:
        1 * progressLogger.completed("Downloaded toolchain $uri", false)
        1 * buildProgressListener.onEvent({ DefaultFileDownloadFinishEvent event ->
            event.result instanceof DefaultFileDownloadSuccessResult
            event.result.bytesDownloaded == 4096
            event.result.startTime == 100
            event.result.endTime == 101
            event.displayName == "Download $uri finished"
            event.descriptor.name == "Download $uri"
            event.descriptor.uri == uri
        })
    }

    def "download failed event"() {
        when:
        progressListener.downloadFailed(uri, new Exception("download failed"),4096, 100, 101)

        then:
        1 * progressLogger.completed("Failed to download toolchain $uri", true)
        1 * buildProgressListener.onEvent({ DefaultFileDownloadFinishEvent event ->
            event.result instanceof DefaultFileDownloadFailureResult
            event.result.bytesDownloaded == 4096
            event.result.startTime == 100
            event.result.endTime == 101
            event.displayName == "Download $uri failed"
            event.descriptor.name == "Download $uri"
            event.descriptor.uri == uri
        })
    }
}
