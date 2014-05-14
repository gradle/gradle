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

package org.gradle.plugin.use.resolve.internal

import org.gradle.api.Plugin
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.groovy.scripts.StringScriptSource
import org.gradle.plugin.use.internal.DefaultPluginRequest
import org.gradle.plugin.use.internal.InvalidPluginRequestException
import org.gradle.plugin.use.internal.PluginRequest
import spock.lang.Specification

class CorePluginResolverTest extends Specification {

    static class MyPlugin implements Plugin {
        @Override
        void apply(Object target) {
        }
    }

    def docRegistry = Mock(DocumentationRegistry)
    def pluginRegistry = Mock(PluginRegistry)

    def resolver = new CorePluginResolver(docRegistry, pluginRegistry)

    PluginRequest request(String id, String version = null) {
        new DefaultPluginRequest(id, version, 1, new StringScriptSource("test", "test"))
    }

    def "non core plugins are ignored"() {
        expect:
        resolver.resolve(request("foo.bar")) == null
    }

    def "can resolve unqualified"() {
        when:
        def resolution = resolver.resolve(request("foo"))

        then:
        1 * pluginRegistry.getTypeForId("foo") >> MyPlugin

        resolution instanceof SimplePluginResolution
        resolution.resolve() == MyPlugin
    }

    def "can resolve qualified"() {
        when:
        def resolution = resolver.resolve(request("${CorePluginResolver.CORE_PLUGIN_NAMESPACE}.foo"))

        then:
        1 * pluginRegistry.getTypeForId("foo") >> MyPlugin

        resolution instanceof SimplePluginResolution
        resolution.resolve() == MyPlugin
    }

    def "cannot have version number"() {
        when:
        resolver.resolve(request("foo", "1.0"))

        then:
        1 * pluginRegistry.getTypeForId("foo") >> MyPlugin

        and:
        thrown InvalidPluginRequestException
    }

    def "ignores plugin with version that isn't found in registry"() {
        when:
        def resolution = resolver.resolve(request("foo", "1.0"))

        then:
        1 * pluginRegistry.getTypeForId("foo") >> { throw new UnknownPluginException("foo") }

        and:
        resolution == null
    }

}
