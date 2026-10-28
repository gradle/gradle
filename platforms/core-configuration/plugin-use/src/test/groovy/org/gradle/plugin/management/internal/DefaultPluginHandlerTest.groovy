/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.buildinit.specs.internal.BuildInitSpecRegistry
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class DefaultPluginHandlerTest extends Specification {
    private AutoAppliedPluginRegistry autoAppliedPluginRegistry = Mock(AutoAppliedPluginRegistry)
    private DefaultPluginHandler pluginHandler = new DefaultPluginHandler(autoAppliedPluginRegistry)

    def "requests single plugin from system prop"() {
        given:
        System.setProperty(BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER, "org.example.plugin:2.2.0")

        when:
        PluginRequests requests = pluginHandler.getArgumentSourcedPlugins()

        then:
        requests.size() == 1
        requests[0].getId().toString() == "org.example.plugin"
        requests[0].getVersion() == "2.2.0"
    }

    def "requests multiple plugins from system prop"() {
        given:
        System.setProperty(BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER, "org.example.plugin:2.2.0,com.other.plugin:1.0")

        when:
        PluginRequests requests = pluginHandler.getArgumentSourcedPlugins()

        then:
        requests.size() == 2
        requests[0].getId().toString() == "org.example.plugin"
        requests[0].getVersion() == "2.2.0"
        requests[1].getId().toString() == "com.other.plugin"
        requests[1].getVersion() == "1.0"
    }

    def "requests nothing if no arg provided"() {
        when:
        PluginRequests requests = pluginHandler.getArgumentSourcedPlugins()

        then:
        requests.size() == 0
    }

    def "reports error if invalid arg provided: #prop"() {
        given:
        if (prop != null) {
            System.setProperty(BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER, prop)
        }

        when:
        pluginHandler.getArgumentSourcedPlugins()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Invalid plugin format: '$prop'. Expected format is 'id:version'."

        where:
        prop << ["", " ", "nonsense"]
    }

    def "reports error with one valid and one invalid arg provided"() {
        given:
        System.setProperty(BuildInitSpecRegistry.BUILD_INIT_SPECS_PLUGIN_SUPPLIER, "org.example.plugin:2.2.0,invalid")

        when:
        pluginHandler.getArgumentSourcedPlugins()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Invalid plugin format: 'invalid'. Expected format is 'id:version'."
    }
}
