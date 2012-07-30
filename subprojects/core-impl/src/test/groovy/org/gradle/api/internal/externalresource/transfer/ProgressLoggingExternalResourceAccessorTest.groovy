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

package org.gradle.api.internal.externalresource.transfer

import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream
import org.gradle.api.internal.externalresource.ExternalResource
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

class ProgressLoggingExternalResourceAccessorTest extends Specification {

    def accessor = Mock(ExternalResourceAccessor)
    def progressLoggerFactory = Mock(ProgressLoggerFactory);
    def progressLoggerAccessor = new ProgressLoggingExternalResourceAccessor(accessor, progressLoggerFactory)
    def progressLogger = Mock(ProgressLogger)
    def externalResource = Mock(ExternalResource)

    @Unroll
    def "delegates all #method to delegate resource accessor"() {
        when:
        progressLoggerAccessor."$method"("location")
        then:
        1 * accessor."$method"("location")
        where:
        method << ['getMetaData', 'getResource', 'getResourceSha1']
    }


    def "writeTo wraps delegate in progress logger"() {
        setup:
        accessor.getResource("location") >> externalResource
        externalResource.getName() >> "test resource"
        when:
        def processLoggableResource = progressLoggerAccessor.getResource("location")
        and:
        processLoggableResource.writeTo(new ByteOutputStream())
        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.started()
        1 * progressLogger.completed()
    }
}
