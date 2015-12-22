/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.results

import spock.lang.Specification

class TestPageGeneratorTest extends Specification {

    def "transforms a single commit id to a url"() {
        when:
        def urls = new TestPageGenerator().urlify(['123456'])

        then:
        urls[0].value == 'https://github.com/gradle/gradle/commit/123456'
    }

    def "transforms two commit ids to urls"() {
        when:
        def urls = new TestPageGenerator().urlify(['123456', 'abcdefg'])

        then:
        urls[0].value == 'https://github.com/gradle/gradle/commit/123456'
        urls[1].value == 'https://github.com/gradle/dotcom/commit/abcdefg'
    }

    def "accepts no vcs commit ids"() {
        expect:
        new TestPageGenerator().urlify(inputs).isEmpty()

        where:
        inputs << [[], null]
    }
}
