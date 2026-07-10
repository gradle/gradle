/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.gcp.gcs

import spock.lang.Specification

class GcsConnectionPropertiesTest extends Specification {

    def "should report invalid scheme"() {
        when:
        new GcsConnectionProperties(endpoint, null, null)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "System property [org.gradle.gcs.endpoint=$endpoint] must have a scheme of 'http' or 'https'"

        where:
        endpoint << ['httpd//somewhere', 'httpd://somewhere', 'gcs://somewhere']
    }

    def "should report invalid uri"() {
        when:
        new GcsConnectionProperties('httpdasd%:/ads', null, null)
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "System property [org.gradle.gcs.endpoint=httpdasd%:/ads]  must be a valid URI"
    }

    def "should allow case insensitive schemes"() {
        expect:
        endpoint == new GcsConnectionProperties(endpoint, null, null).getEndpoint().get().toString()
        where:
        endpoint << ['http://some', 'httP://some', 'httpS://some', 'HTTpS://some']
    }

    def "should default invalid disableAuthentication parameter"() {
        expect:
        new GcsConnectionProperties("http://some", null, value).requiresAuthentication()
        then:
        where:
        value << ['foo', 'true-ish', 'false-y', '1']
    }
}
