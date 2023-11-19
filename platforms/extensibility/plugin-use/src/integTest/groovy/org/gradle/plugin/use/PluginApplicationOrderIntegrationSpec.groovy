/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import spock.lang.Issue

class PluginApplicationOrderIntegrationSpec extends AbstractPluginSpec {
    @Issue('https://github.com/gradle/gradle/issues/15664')
    def 'plugins are applied in the order they are declared in the plugins block'() {
        given:
        publishPlugin """
            if (!project.pluginManager.hasPlugin('java')) {
                throw new Exception('plugin application order is not correct')
            }
        """

        when:
        buildScript """
            plugins {
                id 'java'
                id '$PLUGIN_ID' version '$VERSION'
            }
        """

        then:
        succeeds('help')
    }
}
