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

public class VersionRangeMatcherTest extends Specification {
    def strategy = new LatestVersionStrategy()
    def matcher = new VersionRangeMatcher(strategy)

    def setup() {
        strategy.versionMatcher = matcher
    }

    def "classifies selectors describing a range as dynamic"() {
        expect:
        matcher.isDynamic("[1.0,2.0]")
        matcher.isDynamic("[1.0,2.0[")
        matcher.isDynamic("]1.0,2.0]")
        matcher.isDynamic("]1.0,2.0[")
        matcher.isDynamic("[1.0,)")
        matcher.isDynamic("]1.0,)")
        matcher.isDynamic("(,2.0]")
        matcher.isDynamic("(,2.0[")
    }

    def "classifies all other selectors as not dynamic"() {
        expect:
        !matcher.isDynamic("1")
        !matcher.isDynamic("1+")
    }

    def "doesn't need metadata"() {
        expect:
        !matcher.needModuleMetadata("[1.0,2.0]", "1.0")
        !matcher.needModuleMetadata("[1.0,)", "1.0")
        !matcher.needModuleMetadata("1", "1")
    }

    def "accepts candidate versions that fall into the selector's range"() {
        expect:
        matcher.accept("[1.0,2.0]", "1.0")
        matcher.accept("[1.0,2.0]", "1.2.3")
        matcher.accept("[1.0,2.0]", "2.0")

        matcher.accept("[1.0,2.0[", "1.0")
        matcher.accept("[1.0,2.0[", "1.2.3")
        matcher.accept("[1.0,2.0[", "1.99")

        matcher.accept("]1.0,2.0]", "1.0.1")
        matcher.accept("]1.0,2.0]", "1.2.3")
        matcher.accept("]1.0,2.0]", "2.0")

        matcher.accept("]1.0,2.0[", "1.0.1")
        matcher.accept("]1.0,2.0[", "1.2.3")
        matcher.accept("]1.0,2.0[", "1.99")

        matcher.accept("[1.0,)", "1.0")
        matcher.accept("[1.0,)", "1.2.3")
        matcher.accept("[1.0,)", "2.3.4")

        matcher.accept("]1.0,)", "1.0.1")
        matcher.accept("]1.0,)", "1.2.3")
        matcher.accept("]1.0,)", "2.3.4")

        matcher.accept("(,2.0]", "0")
        matcher.accept("(,2.0]", "0.1.2")
        matcher.accept("(,2.0]", "2.0")

        matcher.accept("(,2.0[", "0")
        matcher.accept("(,2.0[", "0.1.2")
        matcher.accept("(,2.0[", "1.99")
    }

    def "rejects candidate versions that don't fall into the selector's range"() {
        expect:
        !matcher.accept("[1.0,2.0]", "0.99")
        !matcher.accept("[1.0,2.0]", "2.0.1")
        !matcher.accept("[1.0,2.0]", "42")

        !matcher.accept("[1.0,2.0[", "0.99")
        !matcher.accept("[1.0,2.0[", "2.0")
        !matcher.accept("[1.0,2.0[", "42")

        !matcher.accept("]1.0,2.0]", "1.0")
        !matcher.accept("]1.0,2.0]", "2.0.1")
        !matcher.accept("]1.0,2.0]", "42")

        !matcher.accept("]1.0,2.0[", "1.0")
        !matcher.accept("]1.0,2.0[", "2.0")
        !matcher.accept("]1.0,2.0[", "42")

        !matcher.accept("[1.0,)", "0")
        !matcher.accept("[1.0,)", "0.99")

        !matcher.accept("]1.0,)", "0")
        !matcher.accept("]1.0,)", "1")
        !matcher.accept("]1.0,)", "1.0")

        !matcher.accept("(,2.0]", "2.0.1")
        !matcher.accept("(,2.0]", "42")

        !matcher.accept("(,2.0[", "2.0")
        !matcher.accept("(,2.0[", "42")
    }

    def "compares candidate versions against the selector's upper bound"() {
        def comparator = new LatestVersionStrategy.VersionComparator()

        expect:
        matcher.compare(range, "0.5", comparator) > 0
        matcher.compare(range, "1.0", comparator) > 0
        matcher.compare(range, "1.5", comparator) > 0
        matcher.compare(range, "2.0", comparator) < 0 // unsure why [1.0,2.0] isn't considered equal to 2.0 (apparently never returns 0)
        matcher.compare(range, "2.5", comparator) < 0

        where:
        range       | _
        "[1.0,2.0]" | _
        "[1.0,2.0[" | _
        "]1.0,2.0]" | _
        "]1.0,2.0[" | _
        "(,2.0]"    | _
        "(,2.0["    | _
    }

    def "selectors with infinite upper bound compare greater than any candidate version"() {
        def comparator = new LatestVersionStrategy.VersionComparator()

        expect:
        matcher.compare(range, "0.5", comparator) > 0
        matcher.compare(range, "1.0", comparator) > 0
        matcher.compare(range, "1.5", comparator) > 0
        matcher.compare(range, "2.0", comparator) > 0
        matcher.compare(range, "2.5", comparator) > 0

        where:
        range    | _
        "[1.0,)" | _
        "]1.0,)" | _
    }
}
