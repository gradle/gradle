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

package org.gradle.internal.resource

import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.resources.MissingResourceException
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Specification

class UriTextResourceIntegrationTest extends Specification {

    @Rule
    final BlockingHttpServer server = new BlockingHttpServer()

    def "has no content when using HTTP URI and file does not exist"() {
        given:
        server.start()
        String unknownPath = '/unknown.txt'
        String fullURI = "${server.uri}${unknownPath}"
        UriTextResource resource = new UriTextResource('<display-name>', new URI(fullURI), new IdentityFileResolver())

        when:
        server.expect(server.get(unknownPath).missing())
        boolean exists = resource.exists

        then:
        !exists

        when:
        server.expect(server.get(unknownPath).missing())
        resource.text

        then:
        def e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$fullURI' as it does not exist." as String
    }
}
