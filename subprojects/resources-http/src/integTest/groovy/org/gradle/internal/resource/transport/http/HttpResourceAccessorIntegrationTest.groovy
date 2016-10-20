/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.transport.http

import org.apache.http.client.methods.CloseableHttpResponse
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class HttpResourceAccessorIntegrationTest  extends Specification {
    URI uri = new URI("http://somewhere")

    def "should not generate any concurrent exception"() {
        def executor = Executors.newCachedThreadPool()
        def http = Mock(HttpClientHelper) {
            performGet(uri.toString(), _) >> Mock(CloseableHttpResponse)
        }
        def httpResourceAccessor = new HttpResourceAccessor(http)
        def hasConcurrentFailure = new AtomicBoolean(false)

        when:
        (1..10).each { thread ->
            executor.execute {
                try {
                    (1..100).each {
                        httpResourceAccessor.openResource(uri, false).close()
                    }
                } catch (ConcurrentModificationException) {
                    hasConcurrentFailure.set(true)
                }

            }
        }
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.SECONDS)

        then:
        hasConcurrentFailure.get() == false
    }
}
