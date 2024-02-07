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
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.plugins.PluginImplementation
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.plugin.management.internal.DefaultPluginRequest
import org.gradle.plugin.management.internal.InvalidPluginRequestException
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.internal.DefaultPluginId
import spock.lang.Specification

class CorePluginResolverTest extends Specification {

    static class MyPlugin implements Plugin {
        @Override
        void apply(Object target) {
        }
    }

    def docRegistry = new DocumentationRegistry()
    def pluginRegistry = Mock(PluginRegistry)
    def impl = Mock(PluginImplementation)
    def pluginManager = Mock(PluginManagerInternal)

    def resolver = new CorePluginResolver(docRegistry, pluginRegistry)

    PluginRequestInternal request(String id, String version = null) {
        new DefaultPluginRequest(DefaultPluginId.of(id), true, PluginRequestInternal.Origin.OTHER, "source display name", 1, version, null, null, null)
    }

    def "non core plugins are ignored"() {
        given:
        def request = request("foo.bar")

        when:
        def result = resolver.resolve(request)
        result.getFound(request)

        then:
        def e = thrown(LocationAwareException)
        e.cause.message.contains("plugin is not in 'org.gradle' namespace")
    }

    def "can resolve unqualified"() {
        when:
        def request = request("foo")
        def result = resolver.resolve(request)
        result.getFound(request).applyTo(pluginManager)

        then:
        1 * pluginManager.apply(impl)
        1 * pluginRegistry.lookup(DefaultPluginId.of("foo")) >> impl
    }

    def "can resolve qualified"() {
        when:
        def request = request("org.gradle.foo")
        def result = resolver.resolve(request)
        result.getFound(request).applyTo(pluginManager)

        then:
        1 * pluginManager.apply(impl)
        1 * pluginRegistry.lookup(DefaultPluginId.of("org.gradle.foo")) >> impl
    }

    def "cannot have version number"() {
        when:
        resolver.resolve(request("foo", "1.0"))

        then:
        1 * pluginRegistry.lookup(DefaultPluginId.of("foo")) >> impl

        and:
        thrown InvalidPluginRequestException
    }

    def "cannot have custom artifact"() {
        when:
        resolver.resolve(new DefaultPluginRequest(DefaultPluginId.of("foo"), true, PluginRequestInternal.Origin.OTHER, "test", 1, null, Mock(ModuleVersionSelector), null, null))

        then:
        1 * pluginRegistry.lookup(DefaultPluginId.of("foo")) >> impl

        and:
        thrown InvalidPluginRequestException
    }

    def "ignores plugin with version that isn't found in registry"() {
        given:
        def request = request("org.gradle.foo", "1.0")

        when:
        def resolved = resolver.resolve(request)
        resolved.getFound(request)

        then:
        1 * pluginRegistry.lookup(DefaultPluginId.of("org.gradle.foo")) >> null

        and:
        def e = thrown(LocationAwareException)
        e.cause.message.contains("not a core plugin. " + new DocumentationRegistry().getDocumentationRecommendationFor("available plugins","plugin_reference"))
    }

}
