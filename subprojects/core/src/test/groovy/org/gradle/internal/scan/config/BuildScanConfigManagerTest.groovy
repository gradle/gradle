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
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class BuildScanConfigManagerTest extends Specification {

    boolean scanEnabled
    boolean scanDisabled

    def attributes = Mock(BuildScanConfig.Attributes)

    def "conveys configuration"() {
        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == null
            attributes.is this.attributes
        }

        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == null
            attributes.is this.attributes
        }

        when:
        scanEnabled = false
        scanDisabled = true

        then:
        with(config()) {
            !enabled
            disabled
            attributes.is this.attributes
        }
    }

    @RestoreSystemProperties
    def "can convey unsupported"() {
        when:
        System.setProperty(BuildScanPluginCompatibility.UNSUPPORTED_TOGGLE, "true")

        then:
        with(config()) {
            !enabled
            !disabled
            unsupportedMessage == BuildScanPluginCompatibility.UNSUPPORTED_TOGGLE_MESSAGE
        }

        when:
        scanEnabled = true

        then:
        with(config()) {
            enabled
            !disabled
            unsupportedMessage == BuildScanPluginCompatibility.UNSUPPORTED_TOGGLE_MESSAGE
        }
    }

    def "fails if plugin version is not supported"() {
        when:
        // 1.16 is older than BuildScanPluginCompatibility.MIN_SUPPORTED_VERSION, hence not supported
        config("1.16")

        then:
        thrown(UnsupportedBuildScanPluginVersionException)
    }

    BuildScanConfigManager manager() {
        def startParameter = Mock(StartParameter) {
            isBuildScan() >> scanEnabled
            isNoBuildScan() >> scanDisabled
            getSystemPropertiesArgs() >> { [:] }
        }

        new BuildScanConfigManager(startParameter, Mock(ListenerManager), new BuildScanPluginCompatibility(), { attributes })
    }

    BuildScanConfig config(String versionNumber = BuildScanPluginCompatibility.FIRST_GRADLE_ENTERPRISE_PLUGIN_VERSION) {
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
