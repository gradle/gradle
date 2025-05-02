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

import org.gradle.internal.resource.ExternalResourceName
import org.gradle.util.ConcurrentSpecification
import spock.lang.Issue

class HttpResourceAccessorIntegrationTest extends ConcurrentSpecification {
    def uri = new URI("http://somewhere")
    def name = new ExternalResourceName(uri)

    @Issue("GRADLE-3574")
    def "should not generate any concurrent exception"() {
        def http = Mock(HttpClientHelper) {
            performGet(uri.toString(), _) >> Mock(HttpClientResponse)
        }
        def httpResourceAccessor = new HttpResourceAccessor(http)

        when:
        10.times {
            concurrent.start {
                100.times {
                    httpResourceAccessor.openResource(name, false).close()
                }
            }
        }
        concurrent.finished()

        then:
        noExceptionThrown()
    }
}
