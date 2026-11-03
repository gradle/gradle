/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.test.fixtures.hegel

import org.gradle.test.fixtures.hegel.fixtures.MixedTests
import org.junit.platform.launcher.PostDiscoveryFilter
import org.junit.platform.testkit.engine.EngineTestKit
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class HegelTestFilterTest extends Specification {

    def "#mode mode runs #expected"() {
        when:
        def events = EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(MixedTests))
            .filters(new HegelTestFilter(mode))
            .execute()
            .allEvents()

        then:
        // A @HegelTest is a test template, which reports its run as a container
        events.started().list()
            .collect { it.testDescriptor.displayName }
            .findAll { it in ["property(TestCase)", "plainTest()"] }
            .toSet() == expected.toSet()
        events.failed().count() == 0

        where:
        mode                         | expected
        HegelTestFilter.Mode.INCLUDE | ["property(TestCase)", "plainTest()"]
        HegelTestFilter.Mode.ONLY    | ["property(TestCase)"]
        HegelTestFilter.Mode.EXCLUDE | ["plainTest()"]
    }

    def "the launcher finds the filter"() {
        expect:
        ServiceLoader.load(PostDiscoveryFilter).iterator().toList()
            .any { it instanceof HegelTestFilter }
    }

    @RestoreSystemProperties
    def "the system property '#value' sets the mode"() {
        given:
        if (value == null) {
            System.clearProperty(HegelTestFilter.MODE_PROPERTY)
        } else {
            System.setProperty(HegelTestFilter.MODE_PROPERTY, value)
        }

        expect:
        runWithServiceLoaderFilter() == expected.toSet()

        where:
        value     | expected
        null      | ["property(TestCase)", "plainTest()"]
        "include" | ["property(TestCase)", "plainTest()"]
        "only"    | ["property(TestCase)"]
        "exclude" | ["plainTest()"]
    }

    @RestoreSystemProperties
    def "rejects an unknown mode"() {
        given:
        System.setProperty(HegelTestFilter.MODE_PROPERTY, "true")

        when:
        new HegelTestFilter()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("'true'")
    }

    private static Set<String> runWithServiceLoaderFilter() {
        EngineTestKit.engine("junit-jupiter")
            .selectors(selectClass(MixedTests))
            .filters(new HegelTestFilter())
            .execute()
            .allEvents()
            .started()
            .list()
            .collect { it.testDescriptor.displayName }
            .findAll { it in ["property(TestCase)", "plainTest()"] }
            .toSet()
    }
}
