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

import org.gradle.api.internal.BuildType
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService
import org.gradle.internal.enterprise.impl.legacy.UnsupportedBuildScanPluginVersionException
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class LegacyGradleEnterprisePluginCheckInServiceTest extends Specification {

    boolean scanEnabled
    boolean scanDisabled

    def "conveys configuration"() {
        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == null
        }

        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == null
        }

        when:
        scanEnabled = false
        scanDisabled = true

        then:
        with(config()) {
            !enabled
            disabled
        }
    }

    @RestoreSystemProperties
    def "can convey unsupported"() {
        when:
        System.setProperty(LegacyGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE, "true")

        then:
        with(config()) {
            !enabled
            !disabled
            unsupportedMessage == LegacyGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE_MESSAGE
        }

        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == LegacyGradleEnterprisePluginCheckInService.UNSUPPORTED_TOGGLE_MESSAGE
        }
    }

    def "fails if plugin version is not supported"() {
        when:
        // Earliest plugin version that does not cause an exception is 3.0
        config("2.4.2")

        then:
        thrown(UnsupportedBuildScanPluginVersionException)
    }

    LegacyGradleEnterprisePluginCheckInService manager() {
        def gradle = Mock(GradleInternal) {
            getStartParameter() >> Mock(StartParameterInternal) {
                isBuildScan() >> scanEnabled
                isNoBuildScan() >> scanDisabled
                getSystemPropertiesArgs() >> { [:] }
            }
        }

        new LegacyGradleEnterprisePluginCheckInService(
            gradle,
            new GradleEnterprisePluginManager(),
            BuildType.TASKS
        )
    }

    BuildScanConfig config(String versionNumber = LegacyGradleEnterprisePluginCheckInService.FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) {
        def manager = manager()
        manager.collect(new BuildScanPluginMetadata() {
            @Override
            String getVersion() {
                versionNumber
            }
        })
    }
}
