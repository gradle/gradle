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

package org.gradle.internal.resource.transfer

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Specification

class ProgressLoggingExternalResourceAccessorTest extends Specification {

    ExternalResourceAccessor accessor = Mock()
    ProgressLoggerFactory progressLoggerFactory = Mock()
    ProgressLoggingExternalResourceAccessor progressLoggerAccessor = new ProgressLoggingExternalResourceAccessor(accessor, progressLoggerFactory)
    ProgressLogger progressLogger = Mock()
    ExternalResourceReadResponse externalResource = Mock()
    ExternalResourceMetaData metaData = Mock()
    ExternalResourceAccessor.ContentAndMetadataAction action = Mock()
    URI location = new URI("location")

    def setup() {
        externalResource.metaData >> metaData
    }

    def "getResource returns null when delegate returns null"() {
        setup:
        accessor.withContent(location, false, _) >> null

        when:
        def result = progressLoggerAccessor.withContent(location, false, action)

        then:
        result == null

        and:
        0 * action._
    }

    def "wraps response in delegate"() {
        setup:
        expectResourceRead(location, metaData, new ByteArrayInputStream())

        when:
        def result = progressLoggerAccessor.withContent(location, false, action)

        then:
        result == "result"

        and:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()

        then:
        1 * action.execute(_, _) >> "result"

        then:
        1 * progressLogger.completed()
    }

    def "fires progress events as content is read"() {
        setup:
        metaData.getContentLength() >> 4096
        expectResourceRead(location, metaData, new ByteArrayInputStream(new byte[4096]))

        when:
        def result = progressLoggerAccessor.withContent(location, false, action)

        then:
        result == "result"

        and:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * action.execute(_, _) >> { metaData, inputStream ->
            inputStream.read()
            inputStream.read()
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
            "result"
        }
        1 * progressLogger.progress('1.5 KiB/4 KiB downloaded')
        1 * progressLogger.progress('3 KiB/4 KiB downloaded')
        1 * progressLogger.progress('4 KiB/4 KiB downloaded')
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    def "fires complete event when response closed with partially read stream"() {
        setup:
        metaData.getContentLength() >> 4096
        expectResourceRead(location, metaData, new ByteArrayInputStream(new byte[4096]))

        when:
        progressLoggerAccessor.withContent(location, false, action)

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * action.execute(_, _) >> { metaData, inputStream ->
            inputStream.read(new byte[1600])
            "result"
        }
        1 * progressLogger.progress('1.5 KiB/4 KiB downloaded')
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    def "no progress events logged for resources smaller 1024 bytes"() {
        setup:
        metaData.getContentLength() >> 1023
        expectResourceRead(location, metaData, new ByteArrayInputStream(new byte[1023]))

        when:
        progressLoggerAccessor.withContent(location, false, action)

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * action.execute(_, _) >> { metaData, inputStream ->
            inputStream.read(new byte[1024])
            "result"
        }
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    def expectResourceRead(URI location, ExternalResourceMetaData metaData, InputStream inputStream) {
        1 * accessor.withContent(location, false, _) >> { uri, revalidate, action ->
            action.execute(metaData, inputStream)
        }
    }
}
