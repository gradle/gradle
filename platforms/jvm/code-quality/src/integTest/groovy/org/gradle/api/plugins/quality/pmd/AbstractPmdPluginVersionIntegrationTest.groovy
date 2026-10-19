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
import org.gradle.test.preconditions.OsTestPreconditions

import org.gradle.util.internal.VersionNumber

@TargetCoverage({ PmdCoverage.getSupportedVersionsByJdk() })
class AbstractPmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {

    Set<String> calculateDefaultDependencyNotation() {
        return PmdPlugin.calculateDefaultDependencyNotation(versionNumber.toString())
    }

    static String pmdDeprecationMessage(String pmdVersion) {
        "Using PMD versions older than 7.0.0 has been deprecated. " +
            "This will fail with an error in Gradle 10. " +
            "Detected PMD ${pmdVersion}. " +
            "Upgrade to PMD 7.0.0 or newer. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_pmd_older_than_7"
    }

    /**
     * Expects the deprecation for PMD versions older than 7.0.0 in the next build, if {@code pmdVersion} is such a version.
     * Call it only for builds that execute a Pmd task action.
     */
    void expectPmdDeprecationIfLegacy(String pmdVersion = version.toString()) {
        if (VersionNumber.parse(pmdVersion) < VersionNumber.parse("7.0.0")) {
            executer.expectDocumentedDeprecationWarning(pmdDeprecationMessage(pmdVersion))
        }
    }

    static boolean fileLockingIssuesSolved() {
        return TestPrecondition.satisfied(OsTestPreconditions.Windows) || VersionNumber.parse("5.5.1") <= versionNumber
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
