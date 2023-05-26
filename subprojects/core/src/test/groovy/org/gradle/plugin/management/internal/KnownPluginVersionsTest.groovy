/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugin.management.internal

import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

class KnownPluginVersionsTest extends Specification {

    static def AUTO_APPLIED_GE_PLUGIN_VERSION = AutoAppliedGradleEnterprisePlugin.VERSION
    static def AUTO_APPLIED_GE_PLUGIN_VERSION_NUMBER = VersionNumber.parse(AUTO_APPLIED_GE_PLUGIN_VERSION)
    static def AUTO_APPLIED_NEXT_MINOR_GE_PLUGIN_VERSION = VersionNumber.version(AUTO_APPLIED_GE_PLUGIN_VERSION_NUMBER.major, AUTO_APPLIED_GE_PLUGIN_VERSION_NUMBER.minor + 1).toString()
    static def AUTO_APPLIED_NEXT_MAJOR_GE_PLUGIN_VERSION = VersionNumber.version(AUTO_APPLIED_GE_PLUGIN_VERSION_NUMBER.major + 1, 0).toString()

    def "correct version is selected for the enterprise plugin"() {
        expect:
        def selectedVersion = KnownPluginVersions.getGradleEnterprisePluginSupportedVersion(preferredVersion)
        selectedVersion == expectedVersion

        where:
        preferredVersion                          | expectedVersion
        "1.0"                                     | AUTO_APPLIED_GE_PLUGIN_VERSION
        "2.0"                                     | AUTO_APPLIED_GE_PLUGIN_VERSION
        "2.99"                                    | AUTO_APPLIED_GE_PLUGIN_VERSION
        "3.0"                                     | "3.0"
        "3.13"                                    | "3.13"
        AUTO_APPLIED_GE_PLUGIN_VERSION            | AUTO_APPLIED_GE_PLUGIN_VERSION
        AUTO_APPLIED_NEXT_MINOR_GE_PLUGIN_VERSION | AUTO_APPLIED_NEXT_MINOR_GE_PLUGIN_VERSION
        AUTO_APPLIED_NEXT_MAJOR_GE_PLUGIN_VERSION | AUTO_APPLIED_NEXT_MAJOR_GE_PLUGIN_VERSION
    }

}
