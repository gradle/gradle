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

package org.gradle.internal.scan.config

import org.gradle.integtests.fixtures.KotlinScriptIntegrationTest
import org.gradle.internal.scan.config.fixtures.BuildScanAutoApplyFixture
import org.gradle.plugin.management.internal.autoapply.AutoAppliedBuildScanPlugin
import org.gradle.util.Requires

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption
import static org.gradle.internal.scan.config.fixtures.BuildScanAutoApplyFixture.PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX
import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires([KOTLIN_SCRIPT])
class BuildScanAutoApplyKotlinIntegrationTest extends KotlinScriptIntegrationTest {

    private final BuildScanAutoApplyFixture fixture = new BuildScanAutoApplyFixture(testDirectory, mavenRepo)

    def "can automatically apply build scan plugin when --scan is provided on command-line"() {
        given:
        buildFile << """
            task("dummy")
        """

        settingsFile.text = """
            ${fixture.pluginManagement()}
        """ + settingsFile.text

        fixture.publishDummyBuildScanPlugin(AutoAppliedBuildScanPlugin.VERSION, executer)

        when:
        args("--${BuildScanOption.LONG_OPTION}")
        succeeds('dummy')

        then:
        output.contains("${PUBLISHING_BUILD_SCAN_MESSAGE_PREFIX}${AutoAppliedBuildScanPlugin.VERSION}")
    }
}
