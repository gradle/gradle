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

package org.gradle.internal.scan.config.fixtures

import org.gradle.plugin.management.internal.autoapply.AutoAppliedGradleEnterprisePlugin

/**
 * Applies the Gradle Enterprise plugin via the `settings.gradle` script.
 */
class ApplyGradleEnterprisePluginFixture {
    private static final String APPLY_ENTERPRISE_PLUGIN = """
        plugins {
            id('${AutoAppliedGradleEnterprisePlugin.ID}') version('${AutoAppliedGradleEnterprisePlugin.VERSION}')
            id('io.github.gradle.gradle-enterprise-conventions-plugin') version('0.7.6')
        }
    """

    static void applyEnterprisePlugin(File settingsFile) {
        def settingsText = settingsFile.text
        def matcher = settingsText =~ /id[ (]["']com.gradle.enterprise["'][)]? version[ (]["'](.*)["'][)]?/
        if (matcher.find()) {
            settingsFile.text = settingsText.substring(0, matcher.start(1)) + AutoAppliedGradleEnterprisePlugin.VERSION + settingsText.substring(matcher.end(1))
        } else {
            prefixFile(settingsFile, APPLY_ENTERPRISE_PLUGIN)
        }
    }

    private static void prefixFile(File settingsFile, String... prefixes) {
        settingsFile.text = prefixes*.stripIndent()*.trim().join("\n\n") + "\n\n" + settingsFile.text
    }
}
