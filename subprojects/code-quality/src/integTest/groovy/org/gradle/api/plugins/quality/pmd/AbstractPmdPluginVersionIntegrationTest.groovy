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
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.TestPrecondition
import org.gradle.util.VersionNumber

@TargetVersions([PmdPlugin.DEFAULT_PMD_VERSION, '4.3', '5.0.5', '5.1.1', '5.3.3', '6.0.0', '6.8.0'])
class AbstractPmdPluginVersionIntegrationTest extends MultiVersionIntegrationSpec {
    String calculateDefaultDependencyNotation() {
        if (versionNumber < VersionNumber.version(5)) {
            return "pmd:pmd:$version"
        } else if (versionNumber < VersionNumber.parse("5.2.0")) {
            return "net.sourceforge.pmd:pmd:$version"
        }
        return "net.sourceforge.pmd:pmd-java:$version"
    }

    static boolean fileLockingIssuesSolved() {
        return !TestPrecondition.WINDOWS.fulfilled || VersionNumber.parse("5.5.1") <= versionNumber
    }

    static boolean supportIncrementalAnalysis() {
        return versionNumber >= VersionNumber.parse('6.0.0')
    }
}
