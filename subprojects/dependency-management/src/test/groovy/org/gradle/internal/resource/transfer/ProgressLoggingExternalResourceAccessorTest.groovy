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

import org.gradle.internal.resource.ExternalResource
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
    ExternalResource externalResource = Mock()

    @Unroll
    def "delegates #method to delegate resource accessor"() {
        when:
        progressLoggerAccessor."$method"(new URI("location"))

        then:
        1 * accessor."$method"(new URI("location"))

        where:
        method << ['getMetaData', 'getResource']
    }

    def "getResource returns null when delegate returns null"() {
        setup:
        accessor.getResource(new URI("location")) >> null
        when:
        def loadedResource = progressLoggerAccessor.getResource(new URI("location"))
        then:
        loadedResource == null
    }

    def "getResource wraps loaded Resource from delegate in ProgressLoggingExternalResource"() {
        setup:
        accessor.getResource(new URI("location")) >> externalResource
        when:
        def loadedResource = progressLoggerAccessor.getResource(new URI("location"))
        then:
        loadedResource != null
        loadedResource instanceof ProgressLoggingExternalResourceAccessor.ProgressLoggingExternalResource
    }

    def "withContent() wraps delegate call in progress logger"() {
        setup:
        def action = Mock(ExternalResource.ContentAction)
        def metaData = Stub(ExternalResourceMetaData)
        accessor.getResource(new URI("location")) >> externalResource
        externalResource.getName() >> "test resource"
        metaData.getContentLength() >> 4096
        externalResource.withContent(_) >> { ExternalResource.ContentAction a ->
            a.execute(new ByteArrayInputStream(new byte[4096]), metaData)
        }
        action.execute(_, _) >> { InputStream inputStream, ExternalResourceMetaData m ->
            inputStream.read()
            inputStream.read()
            inputStream.read(new byte[560])
            inputStream.read(new byte[1000])
            inputStream.read(new byte[1600])
            inputStream.read(new byte[1024])
            inputStream.read(new byte[1024])
        }

        when:
        progressLoggerAccessor.getResource(new URI("location")).withContent(action)

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.progress('1 KB/4 KB downloaded')
        1 * progressLogger.progress('3 KB/4 KB downloaded')
        1 * progressLogger.completed()
    }

    def "fires complete event when action fails with partially read stream"() {
        setup:
        def action = Mock(ExternalResource.ContentAction)
        def metaData = Stub(ExternalResourceMetaData)
        def failure = new RuntimeException()

        accessor.getResource(new URI("location")) >> externalResource
        externalResource.getName() >> "test resource"
        metaData.getContentLength() >> 4096
        externalResource.withContent(_) >> { ExternalResource.ContentAction a ->
            a.execute(new ByteArrayInputStream(new byte[4096]), metaData)
        }
        action.execute(_, _) >> { InputStream inputStream, ExternalResourceMetaData m ->
            inputStream.read(new byte[1600])
            throw failure
        }

        when:
        progressLoggerAccessor.getResource(new URI("location")).withContent(action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.progress('1 KB/4 KB downloaded')
        1 * progressLogger.completed()
    }

    def "fires complete event when action returns with partially read stream"() {
        setup:
        def action = Mock(ExternalResource.ContentAction)
        def metaData = Stub(ExternalResourceMetaData)

        accessor.getResource(new URI("location")) >> externalResource
        externalResource.getName() >> "test resource"
        metaData.getContentLength() >> 4096
        externalResource.withContent(_) >> { ExternalResource.ContentAction a ->
            a.execute(new ByteArrayInputStream(new byte[4096]), metaData)
        }
        action.execute(_, _) >> { InputStream inputStream, ExternalResourceMetaData m ->
            inputStream.read(new byte[1600])
        }

        when:
        progressLoggerAccessor.getResource(new URI("location")).withContent(action)

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.progress('1 KB/4 KB downloaded')
        1 * progressLogger.completed()
    }

    def "no progress events logged for resources smaller 1024 bytes"() {
        setup:
        def action = Mock(ExternalResource.ContentAction)
        accessor.getResource(new URI("location")) >> externalResource
        externalResource.getName() >> "test resource"
        externalResource.getContentLength() >> 1023
        externalResource.withContent(_) >> { ExternalResource.ContentAction a ->
            a.execute(new ByteArrayInputStream(new byte[1023]), Stub(ExternalResourceMetaData))
        }
        action.execute(_, _) >> { InputStream inputStream, ExternalResourceMetaData metaData ->
            inputStream.read(new byte[1024])
        }

        when:
        progressLoggerAccessor.getResource(new URI("location")).withContent(action)

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.completed()
        0 * progressLogger.progress(_)
    }

    @Unroll
    def "ProgressLoggingExternalResource delegates #method to delegate ExternalResource"() {
        when:
        accessor.getResource(new URI("location")) >> externalResource
        def plExternalResource = progressLoggerAccessor.getResource(new URI("location"))
        and:
        plExternalResource."$method"()
        then:
        1 * externalResource."$method"()
        where:
        method << ['close', 'getMetaData', 'getName', 'isLocal']
    }
}