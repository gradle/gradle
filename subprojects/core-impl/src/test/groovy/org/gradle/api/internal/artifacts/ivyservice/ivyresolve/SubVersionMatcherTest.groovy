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

import com.google.common.collect.Ordering

import spock.lang.Specification

class SubVersionMatcherTest extends Specification {
    def matcher = new SubVersionMatcher()

    def "classifies selectors ending in '+' as dynamic"() {
        expect:
        matcher.isDynamic("1+")
        matcher.isDynamic("1.2.3+")
        !matcher.isDynamic("1")
        !matcher.isDynamic("1.+.3")
    }

    def "doesn't need metadata"() {
        expect:
        !matcher.needModuleMetadata("1+", "1")
        !matcher.needModuleMetadata("1.2.3+", "1.2.3")
    }

    def "accepts candidate versions that literally match the selector up until the trailing '+'"() {
        expect:
        matcher.accept("1+", "1")
        matcher.accept("1+", "1.2")
        matcher.accept("1.2.3+", "1.2.3.11")
        !matcher.accept("1+", "2")
        !matcher.accept("1.2.3+", "1.2")
    }

    def "accepts the same candidate versions whether or not metadata is provided"() {
        expect:
        matcher.accept("1+", "1")
        matcher.accept("1.2.3+", "1.2.3")
        !matcher.accept("1+", "2")
        !matcher.accept("1.2.3+", "1.2")
    }

    def "considers a '+' selector greater than any candidate version it matches"() {
        expect:
        matcher.compare("1+", "1", null) > 0
        matcher.compare("1+", "1.2", null) > 0
        matcher.compare("1.2.3+", "1.2.3.11", null) > 0
    }

    def "falls back to the provided comparator if selector doesn't match candidate version"() {
        expect:
        matcher.compare("1+", "2", Ordering.natural()) < 0
        matcher.compare("1+", "11", Ordering.natural()) > 0
    }
}
