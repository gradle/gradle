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

import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

class ProgressLoggingExternalResourceAccessorTest extends Specification {

    ExternalResourceAccessor accessor = Mock()
    ProgressLoggerFactory progressLoggerFactory = Mock();
    ProgressLoggingExternalResourceAccessor progressLoggerAccessor = new ProgressLoggingExternalResourceAccessor(accessor, progressLoggerFactory)
    ProgressLogger progressLogger = Mock()
    ExternalResourceReadResponse externalResource = Mock()
    ExternalResourceMetaData metaData = Mock()

    def setup() {
        externalResource.metaData >> metaData
    }

    @Unroll
    def "delegates #method to delegate resource accessor"() {
        when:
        progressLoggerAccessor."$method"(new URI("location"))

        then:
        1 * accessor."$method"(new URI("location"))

        where:
        method << ['getMetaData', 'openResource']
    }

    def "getResource returns null when delegate returns null"() {
        setup:
        accessor.openResource(new URI("location")) >> null

        when:
        def loadedResource = progressLoggerAccessor.openResource(new URI("location"))

        then:
        loadedResource == null
    }

    def "wraps response in delegate"() {
        setup:
        accessor.openResource(new URI("location")) >> externalResource

        when:
        def loadedResource = progressLoggerAccessor.openResource(new URI("location"))

        then:
        loadedResource != null
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()

        when:
        loadedResource.close()

        then:
        1 * externalResource.close()
        1 * progressLogger.completed()
    }

    def "fires progress events as content is read"() {
        setup:
        accessor.openResource(new URI("location")) >> externalResource
        metaData.getContentLength() >> 4096
        externalResource.openStream() >> new ByteArrayInputStream(new byte[4096])

        when:
        def resource = progressLoggerAccessor.openResource(new URI("location"))
        def inputStream = resource.openStream()
        inputStream.read()
        inputStream.read()
        inputStream.read(new byte[560])
        inputStream.read(new byte[1000])
        inputStream.read(new byte[1600])
        inputStream.read(new byte[1024])
        inputStream.read(new byte[1024])
        resource.close()

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.progress('1 KB/4 KB downloaded')
        1 * progressLogger.progress('3 KB/4 KB downloaded')
        1 * progressLogger.progress('4 KB/4 KB downloaded')
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    def "fires complete event when response closed with partially read stream"() {
        setup:
        accessor.openResource(new URI("location")) >> externalResource
        metaData.getContentLength() >> 4096
        externalResource.openStream() >> new ByteArrayInputStream(new byte[4096])

        when:
        def resource = progressLoggerAccessor.openResource(new URI("location"))
        def inputStream = resource.openStream()
        inputStream.read(new byte[1600])
        resource.close()

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.progress('1 KB/4 KB downloaded')
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    def "no progress events logged for resources smaller 1024 bytes"() {
        setup:
        accessor.openResource(new URI("location")) >> externalResource
        metaData.getContentLength() >> 1023
        externalResource.openStream() >> new ByteArrayInputStream(new byte[1023])

        when:
        def resource = progressLoggerAccessor.openResource(new URI("location"))
        def inputStream = resource.openStream()
        inputStream.read(new byte[1024])
        resource.close()

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }
}