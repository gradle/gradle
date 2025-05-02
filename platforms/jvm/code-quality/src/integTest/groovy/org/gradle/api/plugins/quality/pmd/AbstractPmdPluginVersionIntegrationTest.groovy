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
import org.gradle.quality.integtest.fixtures.PmdCoverage
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.VersionNumber

@TargetCoverage({ PmdCoverage.getSupportedVersionsByJdk() })
class AbstractPmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    Set<String> calculateDefaultDependencyNotation() {
        return PmdPlugin.calculateDefaultDependencyNotation(versionNumber.toString())
    }
    /**
     * Checks if the current PMD version supports the current Java version, if not we set the sourceCompatibility to the max Java version supported by the current PMD version.
     */
    String requiredSourceCompatibility() {
        if (versionNumber < VersionNumber.parse('6.0.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk9OrLater)) {
            "java.sourceCompatibility = 1.8"
        } else if (versionNumber < VersionNumber.parse('6.4.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk10OrLater)) {
            "java.sourceCompatibility = 9"
        } else if (versionNumber < VersionNumber.parse('6.6.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk11OrLater)) {
            "java.sourceCompatibility = 10"
        } else if (versionNumber < VersionNumber.parse('6.13.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk12OrLater)) {
            "java.sourceCompatibility = 11"
        } else if (versionNumber < VersionNumber.parse('6.18.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk13OrLater)) {
            "java.sourceCompatibility = 12"
        } else if (versionNumber < VersionNumber.parse('6.22.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk14OrLater)) {
            "java.sourceCompatibility = 13"
        } else if (versionNumber < VersionNumber.parse('6.48.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk19OrLater)) {
            "java.sourceCompatibility = 18"
        } else if (versionNumber < VersionNumber.parse('7.0.0') && TestPrecondition.satisfied(UnitTestPreconditions.Jdk21OrLater)) {
            "java.sourceCompatibility = 20"
        } else if (TestPrecondition.satisfied(UnitTestPreconditions.Jdk23OrLater)) { // PMD doesn't support Java 23 yet
            "java.sourceCompatibility = 22"
        } else {
            "" // do not set a source compatibility for the DEFAULT_PMD_VERSION running on latest Java, this way we will catch if it breaks with a new Java version
        }
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
