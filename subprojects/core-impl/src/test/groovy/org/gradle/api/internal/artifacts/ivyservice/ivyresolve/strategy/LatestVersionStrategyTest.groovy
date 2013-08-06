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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo

import spock.lang.Specification

class LatestVersionStrategyTest extends Specification {
    def chain = new ChainVersionMatcher()
    def strategy = new LatestVersionStrategy(chain)
    def matcher = Mock(VersionMatcher)

    def setup() {
        chain.add(new SubVersionMatcher(new ExactVersionMatcher()))
        chain.add(matcher)
        chain.add(new ExactVersionMatcher())
    }

    def "compares static versions according to version matcher"() {
        expect:
        strategy.compare(new VersionInfo("1.0"), new VersionInfo("2.0")) < 0
        strategy.compare(new VersionInfo("1.0"), new VersionInfo("1.0")) == 0
        strategy.compare(new VersionInfo("2.0"), new VersionInfo("1.0")) > 0
    }

    def "compares dynamic and static version according to version matcher"() {
        expect:
        strategy.compare(new VersionInfo("1.+"), new VersionInfo("2.0")) < 0
        strategy.compare(new VersionInfo("2.0"), new VersionInfo("1.+")) > 0
        strategy.compare(new VersionInfo("1.+"), new VersionInfo("1.11")) > 0
        strategy.compare(new VersionInfo("1.11"), new VersionInfo("1.+")) < 0
    }

    def "considers dynamic version greater if it compares equal according to version matcher"() {
        matcher.canHandle("foo") >> true
        matcher.isDynamic("foo") >> true
        matcher.isDynamic("1.0") >> false
        matcher.compare(_, _) >> 0

        expect:
        strategy.compare(new VersionInfo("foo"), new VersionInfo("1.0")) > 0
        strategy.compare(new VersionInfo("1.0"), new VersionInfo("foo")) < 0
    }

    def "sorts elements in ascending order according to version matcher"() {
        def version1 = new VersionInfo("1.0")
        def version2 = new VersionInfo("1.+")
        def version3 = new VersionInfo("2.2")
        def version4 = new VersionInfo("2.+")

        expect:
        strategy.sort([version3, version1, version2, version4]) == [version1, version2, version3, version4]
    }

    def "finds latest element according to version matcher"() {
        def version1 = new VersionInfo("1.0")
        def version2 = new VersionInfo("1.+")
        def version3 = new VersionInfo("2.2")
        def version4 = new VersionInfo("2.+")

        expect:
        strategy.findLatest([version3, version1, version2, version4]) == version4
    }
}
