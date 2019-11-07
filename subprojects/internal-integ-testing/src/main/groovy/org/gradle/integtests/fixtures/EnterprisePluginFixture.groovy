/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin

class EnterprisePluginFixture {
    private static final String APPLY_ENTERPRISE_PLUGIN = """
        plugins {
            id('${AutoAppliedGradleEnterprisePlugin.ID}') version('${AutoAppliedGradleEnterprisePlugin.VERSION}')
        }
    """
    private static final String PUBLISH_SCAN_TO_INTERNAL_SERVER = """
         gradleEnterprise {
            buildScan {
                server = "https://e.grdev.net/"
                captureTaskInputFiles = true
                publishAlways()
            }
        }
    """

    static void applyEnterprisePlugin(File settingsFile) {
        prefixFile(settingsFile, APPLY_ENTERPRISE_PLUGIN)
    }

    static void publishScansToInternalServer(File settingsFile) {
        prefixFile(settingsFile, APPLY_ENTERPRISE_PLUGIN, PUBLISH_SCAN_TO_INTERNAL_SERVER)
    }

    private static void prefixFile(File settingsFile, String... prefixes) {
        settingsFile.text = prefixes*.stripIndent()*.trim().join("\n\n") + "\n\n" + settingsFile.text
    }
}
