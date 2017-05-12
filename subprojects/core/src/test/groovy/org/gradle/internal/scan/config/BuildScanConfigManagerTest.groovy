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

import org.gradle.StartParameter
import org.gradle.internal.event.ListenerManager
import org.gradle.testing.internal.util.Specification
import org.gradle.util.VersionNumber

class BuildScanConfigManagerTest extends Specification {

    boolean scanEnabled
    boolean scanDisabled

    String maxUnsupportedVersion = "0.9"
    String minSupportedVersion = "1.0"
    String pluginVersionNumber = minSupportedVersion

    def "conveys configuration"() {
        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
        }

        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
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

    def "throws if plugin version is too old"() {
        when:
        pluginVersionNumber = "0.9"
        config()

        then:
        thrown UnsupportedBuildScanPluginVersionException

        when:
        pluginVersionNumber = "1.1"
        config()

        then:
        notThrown UnsupportedBuildScanPluginVersionException

        when:
        pluginVersionNumber = "1.0-TIMESTAMP"
        config()

        then:
        notThrown UnsupportedBuildScanPluginVersionException
    }

    BuildScanConfigManager manager() {
        def startParameter = Mock(StartParameter) {
            isBuildScan() >> scanEnabled
            isNoBuildScan() >> scanDisabled
            getSystemPropertiesArgs() >> { [:] }
        }

        new BuildScanConfigManager(startParameter, Mock(ListenerManager), new BuildScanPluginCompatibilityEnforcer(VersionNumber.parse(maxUnsupportedVersion), VersionNumber.parse(minSupportedVersion)))
    }

    BuildScanConfig config(String versionNumber = pluginVersionNumber) {
        def manager = manager()
        manager.init()
        manager.collect(new BuildScanPluginMetadata() {
            @Override
            String getVersion() {
                versionNumber
            }
        })
    }
}
