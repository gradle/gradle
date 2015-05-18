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

package org.gradle.testing.jacoco.tasks.coverage

import com.google.common.io.Resources
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.hamcrest.Matchers
import org.junit.Assert
import spock.lang.Specification

class JacocoCheckCoverageTest extends Specification {

    def getSampleCoverage() {
        def report = Resources.asByteSource(JacocoCheckCoverageTest.class.getResource("/jacocoTestReport.xml"))
        JacocoCheckCoverage.extractCoverageFromReport(report.openBufferedStream())
    }

    def "Parses sample Jacoco XML Report correctly"() {
        when: "A report XML file is parsed"
        def coverage = getSampleCoverage()
        then:
        Assert.assertNotNull(coverage)
        Assert.assertThat(coverage.keySet(), Matchers.containsInAnyOrder("AnotherClass.java", "DummyClass.java"))
        Assert.assertThat(coverage.get("AnotherClass.java").keySet(), Matchers.containsInAnyOrder(CoverageType.INSTRUCTION,
            CoverageType.LINE, CoverageType.COMPLEXITY, CoverageType.METHOD, CoverageType.CLASS))

        // Spot-check some extracted numbers.
        Assert.assertEquals(7, coverage.get("DummyClass.java").get(CoverageType.INSTRUCTION).covered)
        Assert.assertEquals(3, coverage.get("DummyClass.java").get(CoverageType.INSTRUCTION).missed)
    }

    def "No rules imply no violations"() {
        setup: "Empty rule set and non-empty coverage provided"
        def rules = []
        def coverage = getSampleCoverage()
        when: "Rules are applied"
        def violations = JacocoCheckCoverage.applyRules(rules, coverage)
        then:
        assert violations == []
    }

    def "When multiple rules fire, then the one with smaller threshold dominates"() {
        setup: "Rule set and non-empty coverage provided"
        JacocoPluginExtension extension = new JacocoPluginExtension(null, null)
        extension.threshold(0.5)
        extension.threshold(0.1, CoverageType.INSTRUCTION, "AnotherClass.java")
        def rules = extension.coverageRules
        def coverage = getSampleCoverage().findAll { clazz -> clazz.key == "AnotherClass.java" }
        when: "Rules are applied"
        def violations = JacocoCheckCoverage.applyRules(rules, coverage)
        then:
        assert violations.size() == 5
        violations.each { violation ->
            if (violation.type == CoverageType.INSTRUCTION) {
                assert violation.threshold == 0.1
            } else {
                assert violation.threshold == 0.5
            }
        }
    }

    def "Coverage threshold are inclusive lower bound"() {
        setup: "Rule set and non-empty coverage provided"
        JacocoPluginExtension extension = new JacocoPluginExtension(null, null)
        extension.threshold(0.3, CoverageType.INSTRUCTION, "DummyClass.java") // Coverage is exactly 3/(3+7).
        when: "Rules are applied"
        def violations = JacocoCheckCoverage.applyRules(extension.coverageRules, getSampleCoverage())
        then:
        assert violations.isEmpty()
    }

    def "Rules have no effect on coverage types and classes that they don't apply to"() {
        // In particular, this check verifies that the "return 2.0" checks in JacocoPluginExtension#threshold are filtered out.
        setup: "Rule set and non-empty coverage provided"
        JacocoPluginExtension extension = new JacocoPluginExtension(null, null)
        extension.threshold(0.0, CoverageType.INSTRUCTION, "AnotherClass.java")
        when: "Rules are applied"
        def violations = JacocoCheckCoverage.applyRules(extension.coverageRules, getSampleCoverage())
        then:
        assert violations.isEmpty()
    }

    def "A more specific rule with a higher threshold does not overrule a less specific one with smaller threshold"() {
        setup: "Rule set and non-empty coverage provided"
        JacocoPluginExtension extension = new JacocoPluginExtension(null, null)
        extension.threshold(0.0)
        extension.threshold(0.9, CoverageType.INSTRUCTION, "AnotherClass.java") // This rule would fail on its own.
        when: "Rules are applied"
        def violations = JacocoCheckCoverage.applyRules(extension.coverageRules, getSampleCoverage())
        then:
        assert violations.isEmpty()
    }
}
