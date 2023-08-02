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
package org.gradle.api.plugins.quality

import org.gradle.api.plugins.quality.pmd.AbstractPmdPluginVersionIntegrationTest
import org.gradle.util.Matchers
import org.hamcrest.CoreMatchers
import org.junit.Assume

import static org.hamcrest.CoreMatchers.containsString

class PmdPluginIncrementalAnalysisIntegrationTest extends AbstractPmdPluginVersionIntegrationTest {
    def setup() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "pmd"

            ${mavenCentralRepository()}

            pmd {
                toolVersion = '$version'
            }
            ${fileLockingIssuesSolved() ? "" : """
            tasks.withType(Pmd) {
                // clear the classpath to avoid file locking issues on PMD version < 5.5.1
                classpath = files()
            }"""}

            ${requiredSourceCompatibility()}
        """.stripIndent()
    }

    def "incremental analysis cache file is not generated with incremental analysis disabled"() {
        buildFile << 'pmd { incrementalAnalysis = false }'
        goodCode()

        expect:
        succeeds("check", "-i")
        !file("build/tmp/pmdMain/incremental.cache").exists()
    }

    def "incremental analysis can be enabled"() {
        given:
        Assume.assumeTrue(supportIncrementalAnalysis())
        goodCode()

        when:
        succeeds("pmdMain")

        then:
        file("build/tmp/pmdMain/incremental.cache").exists()

        when:
        args('--rerun-tasks', '--info')
        succeeds("pmdMain")

        then:
        !output.contains('Analysis cache invalidated, rulesets changed')
    }

    def 'incremental analysis is transparent'() {
        given:
        Assume.assumeTrue(supportIncrementalAnalysis())
        goodCode()
        badCode()

        when:
        fails('pmdMain')

        then:
        file("build/reports/pmd/main.xml").assertContents(Matchers.containsText('BadClass'))

        when:
        file('src/main/java/org/gradle/BadClass.java').delete()
        succeeds('pmdMain')

        then:
        file("build/reports/pmd/main.xml").assertContents(CoreMatchers.not(containsString('BadClass')))
    }

    def 'incremental analysis invalidated when #reason'() {
        given:
        Assume.assumeTrue(supportIncrementalAnalysis())
        goodCode()
        customRuleSet()

        succeeds('pmdMain')

        when:
        buildFile << "\npmd{${code}}"
        executer.noDeprecationChecks() // PMD complains about outdated rule sets
        succeeds('pmdMain', '--info')

        then:
        outputContains("Analysis cache invalidated, ${reason}")


        where:
        reason                | code
        'PMD version changed' | 'toolVersion="6.5.0"'
        'rulesets changed'    | 'ruleSetFiles = files("customRuleSet.xml")'
    }

    def "incremental analysis is available in 6.0.0 or newer"() {
        given:
        Assume.assumeTrue(supportIncrementalAnalysis())
        goodCode()

        expect:
        succeeds('pmdMain')
    }

    def "incremental analysis fails when enabled with older than 6.0.0"() {
        given:
        Assume.assumeFalse(supportIncrementalAnalysis())
        goodCode()

        when:
        fails('pmdMain')

        then:
        failure.error.contains("Incremental analysis only supports PMD 6.0.0 and newer. Please upgrade from PMD ${versionNumber} or disable incremental analysis.")
    }

    private goodCode() {
        file("src/main/java/org/gradle/GoodClass.java") <<
            "package org.gradle; class GoodClass { public boolean isFoo(Object arg) { return true; } }"
    }

    private badCode() {
        // PMD Lvl 2 Warning BooleanInstantiation
        // PMD Lvl 3 Warning OverrideBothEqualsAndHashcode
        file("src/main/java/org/gradle/BadClass.java") <<
            "package org.gradle; class BadClass { public boolean equals(Object arg) { return java.lang.Boolean.valueOf(true); } }"
    }

    private customRuleSet() {
        file("customRuleSet.xml") << """
            <ruleset name="custom"
                xmlns="http://pmd.sf.net/ruleset/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <description>Custom rule set</description>

                <rule ref="rulesets/java/braces.xml"/>
            </ruleset>
        """
    }

}
