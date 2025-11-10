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

import org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin

import static org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin.CONVENTIONS_PLUGIN_VERSION
import static org.gradle.plugin.management.internal.autoapply.AutoAppliedDevelocityPlugin.VERSION

/**
 * Applies the Develocity plugin via the `settings.gradle` script.
 */
class ApplyDevelocityPluginFixture {
    private static final String APPLY_DEVELOCITY_PLUGIN = """
        |    id("${AutoAppliedDevelocityPlugin.ID}").version("${VERSION}")
        |    id("${AutoAppliedDevelocityPlugin.CONVENTIONS_PLUGIN_ID}").version("${CONVENTIONS_PLUGIN_VERSION}")
        |""".stripMargin()

    static void applyDevelocityPlugin(File settingsFile) {
        def settingsText = settingsFile.text
        def matcher = settingsText =~ /id[ (]["']com.gradle.develocity["'][)]? version[ (]["'](.*)["'][)]?/
        if (matcher.find()) {
            settingsFile.text = settingsText.substring(0, matcher.start(1)) + VERSION + settingsText.substring(matcher.end(1))
        } else {
            insertIntoFile(settingsFile, APPLY_DEVELOCITY_PLUGIN)
        }
    }

    private static void insertIntoFile(File settingsFile, String pluginBlock) {
        def settingsText = settingsFile.text

        def pluginsBlockMatcher = settingsText =~ /(?s)plugins ([{]).*?[}]/
        if (pluginsBlockMatcher.find()) {
           settingsText =
               settingsText.substring(0, pluginsBlockMatcher.end(1)) +
                   pluginBlock +
                   settingsText.substring(pluginsBlockMatcher.end(1))
        } else {
            def pluginManagementMatcher = settingsText =~ /(?s)pluginManagement [{].*[}]/
            if (pluginManagementMatcher.find()) {
                settingsText =
                    settingsText.substring(0, pluginManagementMatcher.end(0)) +
                        "\nplugins {" + pluginBlock + "}" +
                        settingsText.substring(pluginManagementMatcher.end(0))
            } else {
                settingsText = "plugins {$pluginBlock}\n$settingsText"
            }
        }
        settingsFile.text = settingsText
    }
}
