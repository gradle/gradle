/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.core

import org.gradle.integtests.fixtures.KotlinScriptIntegrationTest
import org.gradle.internal.enterprise.GradleEnterprisePluginCheckInFixture

import static org.gradle.initialization.StartParameterBuildOptions.BuildScanOption

class BuildScanAutoApplyKotlinIntegrationTest extends KotlinScriptIntegrationTest {

    private final GradleEnterprisePluginCheckInFixture fixture = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def "can automatically apply plugin when --scan is provided on command-line"() {
        given:
        file("settings.gradle").delete()
        file("settings.gradle.kts") << """
            rootProject.buildFileName = "$defaultBuildFileName"
        """

        buildFile << """
            task("dummy")
        """

        and:
        def initScript = file("init.gradle") << """
            beforeSettings {
                it.with { ${fixture.pluginManagement()} }
            }
        """

        fixture.publishDummyPlugin(executer)

        when:
        args("--${BuildScanOption.LONG_OPTION}", "-I", initScript.absolutePath)
        succeeds('dummy')

        then:
        fixture.appliedOnce(output)
    }

}
