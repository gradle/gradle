/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import spock.lang.Specification

class ExactVersionMatcherTest extends Specification {
    def matcher = new ExactVersionMatcher()

    def "doesn't classify any selector as dynamic"() {
        expect:
        !matcher.isDynamic("1.2.3")
        !matcher.isDynamic("[1.0,2.0]")
    }

    def "doesn't need metadata"() {
        expect:
        !matcher.needModuleMetadata("1.0", "1.0")
        !matcher.needModuleMetadata("[1.0,2.0]", "2.0")
    }

    def "accepts candidate versions that literally match the selector"() {
        expect:
        matcher.accept("1.0", "1.0")
        matcher.accept("2.0", "2.0")
        !matcher.accept("1.0", "1.1")
        !matcher.accept("2.0", "3.0")
    }

    def "accepts the same candidate versions whether or not metadata is available"() {
        expect:
        matcher.accept("1.0", "1.0")
        matcher.accept("2.0", "2.0")
        !matcher.accept("1.0", "1.1")
        !matcher.accept("2.0", "3.0")
    }

    def "doesn't support compare operation (because it doesn't support dynamic selectors)"() {
        when:
        matcher.compare("[1.0,3.0]", "2.0", null)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains("compare")
    }
}
