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
import spock.lang.Unroll
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

    def "throws if plugin version is too old"() {
        when:
        config("1.7")

        then:
        thrown UnsupportedBuildScanPluginVersionException

        when:
        config("1.13")

        then:
        notThrown UnsupportedBuildScanPluginVersionException

        when:
        config("1.13-TIMESTAMP")

        then:
        notThrown UnsupportedBuildScanPluginVersionException
    }

    def "throws if has vcs mappings and plugin version is too old"() {
        given:
        attributes.isRootProjectHasVcsMappings() >> true

        when:
        config("1.9")

        then:
        thrown UnsupportedBuildScanPluginVersionException
    }

    @Unroll
    def "does not throw if has vcs mappings and plugin version #version"() {
        given:
        attributes.isRootProjectHasVcsMappings() >> true

        when:
        config(version)

        then:
        notThrown UnsupportedBuildScanPluginVersionException

        where:
        version << ["1.11", "1.12"]
    }

    @RestoreSystemProperties
    def "throws if kotlin script build caching used and version doesnt support"() {
        given:
        System.setProperty(BuildScanPluginCompatibility.KOTLIN_SCRIPT_BUILD_CACHE_TOGGLE, "true")

        when:
        config("1.9")

        then:
        thrown UnsupportedBuildScanPluginVersionException
    }

    @Unroll
    @RestoreSystemProperties
    def "does not throw if kotlin script build caching used and version #version"() {
        given:
        System.setProperty(BuildScanPluginCompatibility.KOTLIN_SCRIPT_BUILD_CACHE_TOGGLE, "true")

        when:
        config(version)

        then:
        notThrown UnsupportedBuildScanPluginVersionException

        where:
        version << ["1.15.2", "1.16"]
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

    BuildScanConfigManager manager() {
        def startParameter = Mock(StartParameter) {
            isBuildScan() >> scanEnabled
            isNoBuildScan() >> scanDisabled
            getSystemPropertiesArgs() >> { [:] }
        }

        new BuildScanConfigManager(startParameter, Mock(ListenerManager), new BuildScanPluginCompatibility(), { attributes })
    }

    BuildScanConfig config(String versionNumber = BuildScanPluginCompatibility.MIN_SUPPORTED_VERSION) {
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
