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
import org.gradle.internal.resource.ReadableContent
import spock.lang.Specification

class HttpResourceUploaderTest extends Specification {

    def 'uploader closes the request'() {
        given:
        def uri = new URI("http://somewhere.org/somehow")
        def name = new ExternalResourceName(uri)
        def response = Mock(HttpClient.Response) {
            getEffectiveUri() >> uri
            getStatusCode() >> 500
        }
        def client = Mock(HttpClient) {
            performRawPut(_, _) >> response
        }

        when:
        new HttpResourceUploader(client).upload(Mock(ReadableContent), name)

        then:
        HttpErrorStatusCodeException exception = thrown()
        exception.message.contains(uri.toString())
        exception.message.contains("Received status code 500 from server")
        1 * response.close()
    }

}
