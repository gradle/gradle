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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.enterprise.impl.legacy.LegacyGradleEnterprisePluginCheckInService
import org.gradle.internal.enterprise.impl.legacy.UnsupportedBuildScanPluginVersionException
import spock.lang.Specification

class LegacyGradleEnterprisePluginCheckInServiceTest extends Specification {

    def "fails if plugin is not aware of unsupported mechanism"() {
        when:
        // Earliest plugin version that does not cause an exception is 3.0
        config("2.4.2")

        then:
        thrown(UnsupportedBuildScanPluginVersionException)
    }

    def "conveys unsupported without failing"() {
        expect:
        with(config("3.13")) {
            unsupportedMessage == "Gradle Enterprise plugin 3.13 has been disabled as it is incompatible with this version of Gradle. Upgrade to Gradle Enterprise plugin 3.13.1 or newer to restore functionality."
        }
    }

    BuildScanConfig config(String versionNumber, boolean scanEnabled = false, boolean scanDisabled = false) {
        def manager = service(scanEnabled, scanDisabled)
        manager.collect(new BuildScanPluginMetadata() {
            @Override
            String getVersion() {
                versionNumber
            }
        })
    }

    LegacyGradleEnterprisePluginCheckInService service(boolean scanEnabled, boolean scanDisabled) {
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
            Stub(BuildActionModelRequirements)
        )
    }
}
