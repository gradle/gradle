/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories

import spock.lang.Specification
import org.apache.ivy.plugins.repository.TransferEvent
import org.apache.ivy.plugins.repository.Resource
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.ProgressLogger

class ProgressLoggingTransferListenerTest extends Specification {
    TransferEvent transferEvent = Mock()
    Resource resource = Mock()
    ProgressLoggerFactory progressLoggerFactory = Mock()
    ProgressLoggingTransferListener progressLoggingTransferListener = new ProgressLoggingTransferListener(progressLoggerFactory, null)
    ProgressLogger progressLogger = Mock()

    def setup() {
        transferEvent.getResource() >> resource
    }

    def "transferProgress does not log operations on local resources"() {
        setup:
        resource.isLocal() >> true
        when:
        progressLoggingTransferListener.transferProgress(transferEvent)
        then:
        0 * progressLoggerFactory.newOperation(_)
        0 * progressLogger.started()
        0 * progressLogger.progress(_)
        0 * progressLogger.completed()
    }

    def "transferProgress logs started transfers"() {
        setup:
        transferEvent.getEventType() >> TransferEvent.TRANSFER_STARTED
        when:
        progressLoggingTransferListener.transferProgress(transferEvent)
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger

        0 * progressLogger.progress(_)
        0 * progressLogger.completed()
    }

    def "transferProgress logs progress on transfers"() {
        setup:
        progressLoggerFactory.newOperation(_) >> progressLogger
        transferEvent.getLength() >>> [512, 512, 2048, 256]
        transferEvent.getEventType() >>> [TransferEvent.TRANSFER_STARTED, TransferEvent.TRANSFER_PROGRESS, TransferEvent.TRANSFER_PROGRESS, TransferEvent.TRANSFER_PROGRESS, TransferEvent.TRANSFER_PROGRESS]
        when:
        //create progressLogger
        progressLoggingTransferListener.transferProgress(transferEvent)
        and:
        //log progress
        progressLoggingTransferListener.transferProgress(transferEvent)
        progressLoggingTransferListener.transferProgress(transferEvent)
        progressLoggingTransferListener.transferProgress(transferEvent)
        then:
        2 * progressLogger.progress(_)
        0 * progressLogger.completed()
    }

}
