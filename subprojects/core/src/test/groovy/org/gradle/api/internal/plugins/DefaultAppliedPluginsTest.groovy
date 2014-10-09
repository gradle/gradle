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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.model.Model
import org.gradle.model.RuleSource
import spock.lang.Specification

class DefaultAppliedPluginsTest extends Specification {

    def pluginRegistry = Mock(PluginRegistry)
    def pluginContainer = Mock(PluginContainer)
    def target = Mock(PluginAware) {
        toString() >> "PluginAwareMock"
    }
    def appliedPlugins = new DefaultAppliedPlugins(target, pluginRegistry)

    @RuleSource
    static class HasSource {
        @Model
        static String string() { "string" }
    }

    static class PluginWithoutSources implements Plugin<Gradle> {
        void apply(Gradle target) {
        }
    }

    static class NotAPlugin {}

    def "delegates to plugin container if class applied by id implements Plugin"() {
        when:
        target.getPlugins() >> pluginContainer
        pluginRegistry.getTypeForId("plugin-without-sources") >> PluginWithoutSources

        and:
        appliedPlugins.apply("plugin-without-sources")

        then:
        1 * pluginContainer.apply(PluginWithoutSources)
    }

    def "delegates to plugin container if applied class implements Plugin"() {
        when:
        target.getPlugins() >> pluginContainer

        and:
        appliedPlugins.apply(PluginWithoutSources)

        then:
        1 * pluginContainer.apply(PluginWithoutSources)
    }

    def "classes applied by id have to implement Plugin"() {
        when:
        target.getPlugins() >> pluginContainer
        pluginRegistry.getTypeForId("not-a-plugin") >> NotAPlugin

        and:
        appliedPlugins.apply("not-a-plugin")

        then:
        IllegalArgumentException e = thrown()
        e.message == "'${NotAPlugin.name}' does not implement the Plugin interface and only classes that implement it can be applied to 'PluginAwareMock'"
    }

    def "applied classes have to implement Plugin"() {
        when:
        appliedPlugins.apply(NotAPlugin)

        then:
        IllegalArgumentException e = thrown()
        e.message == "'${NotAPlugin.name}' does not implement the Plugin interface and only classes that implement it can be applied to 'PluginAwareMock'"
    }

    def "does not support rule applying source classes"() {
        when:
        appliedPlugins.extractModelRulesAndAdd(HasSource)

        then:
        UnsupportedOperationException e = thrown()
        e.message == "Cannot apply model rules of plugin '${HasSource.name}' as the target 'PluginAwareMock' is not model rule aware"
    }
}
