/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.plugins.quality.pmd

import org.gradle.api.plugins.quality.PmdPlugin
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.jvm.Jvm
import org.gradle.quality.integtest.fixtures.PmdCoverage
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber

import javax.annotation.Nullable

@TargetCoverage({ PmdCoverage.getSupportedVersionsByJdk() })
class AbstractPmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    Set<String> calculateDefaultDependencyNotation() {
        return PmdPlugin.calculateDefaultDependencyNotation(versionNumber.toString())
    }

    /**
     * Checks if the current PMD version supports the current Java version. If not,
     * we set the sourceCompatibility to the max Java version supported by the current PMD version.
     */
    String requiredSourceCompatibility() {
        return requiredSourceCompatibility(Jvm.current().javaVersionMajor, versionNumber)
    }

    static String requiredSourceCompatibility(int javaVersion, VersionNumber pmdVersion) {
        def maxJavaVersion = getMaxJavaVersionFor(pmdVersion)
        if (maxJavaVersion != null && javaVersion > maxJavaVersion) {
            return "java.sourceCompatibility = ${maxJavaVersion}"
        }

        if (javaVersion > 22) {
            return "java.sourceCompatibility = 22" // PMD doesn't support Java 23 yet
        }

        // do not set a source compatibility for the DEFAULT_PMD_VERSION running on latest Java, this way we will catch if it breaks with a new Java version
        return ""
    }

    private static @Nullable Integer getMaxJavaVersionFor(VersionNumber pmdVersion) {
        def versions = [
            '6.0.0': 8,
            '6.4.0': 9,
            '6.6.0': 10,
            '6.13.0': 11,
            '6.18.0': 12,
            '6.22.0': 13,
            '6.48.0': 18,
            '7.0.0': 20
        ]

        for (entry in versions) {
            if (pmdVersion < VersionNumber.parse(entry.key)) {
                return entry.value
            }
        }

        return null
    }

    static boolean fileLockingIssuesSolved() {
        return TestPrecondition.satisfied(UnitTestPreconditions.Windows) || VersionNumber.parse("5.5.1") <= versionNumber
    }

    static boolean supportIncrementalAnalysis() {
        return versionNumber >= VersionNumber.parse('6.0.0')
    }

    static String bracesRuleSetPath() {
        if (versionNumber < VersionNumber.version(5)) {
            "rulesets/braces.xml"
        } else if (versionNumber < VersionNumber.version(6)) {
            "rulesets/java/braces.xml"
        } else if (versionNumber < VersionNumber.version(6, 13)) {
            "category/java/codestyle.xml/IfStmtsMustUseBraces"
        } else {
            "category/java/codestyle.xml/ControlStatementBraces"
        }
    }

    static String customRuleSet() {
        """
            <ruleset name="custom"
                xmlns="http://pmd.sf.net/ruleset/1.0.0"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://pmd.sf.net/ruleset/1.0.0 http://pmd.sf.net/ruleset_xml_schema.xsd"
                xsi:noNamespaceSchemaLocation="http://pmd.sf.net/ruleset_xml_schema.xsd">

                <description>Custom rule set</description>

                <rule ref="${bracesRuleSetPath()}"/>
            </ruleset>
        """
    }
}
