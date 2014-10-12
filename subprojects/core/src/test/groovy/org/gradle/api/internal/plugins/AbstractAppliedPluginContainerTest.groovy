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

import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset
import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.model.RuleSource
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import spock.lang.Specification

class AbstractAppliedPluginContainerTest extends Specification {

    PluginAware target = Mock(PluginAware)
    PluginRegistry pluginRegistry = Mock(PluginRegistry)
    PluginContainer pluginContainer = Mock(PluginContainer)

    TestAppliedPluginContainer container = new TestAppliedPluginContainer(target, pluginRegistry, new ModelRuleSourceDetector())

    static class TestAppliedPluginContainer extends AbstractAppliedPluginContainer {
        Multiset<Class<?>> processedRuleContainers = HashMultiset.create()

        TestAppliedPluginContainer(PluginAware target, PluginRegistry pluginRegistry, ModelRuleSourceDetector modelRuleSourceDetector) {
            super(target, pluginRegistry, modelRuleSourceDetector)
        }

        @Override
        def void extractModelRules(Class<?> pluginClass) {
            processedRuleContainers.add(pluginClass)
        }
    }

    static class TestPlugin implements Plugin<Gradle> {
        void apply(Gradle target) {
        }
    }

    @RuleSource
    static class TestRuleSource {}

    def "delegates to plugin container and extracts rules if class applied by id implements Plugin"() {
        when:
        target.getPlugins() >> pluginContainer
        pluginRegistry.getTypeForId("test-plugin") >> TestPlugin

        and:
        container.apply("test-plugin")

        then:
        1 * pluginContainer.apply(TestPlugin)
        container.processedRuleContainers.count(TestPlugin) == 1

        and:
        container.contains(TestPlugin)
    }

    def "extracts rules if class applied by id does not implement Plugin"() {
        when:
        pluginRegistry.getTypeForId("not-a-plugin") >> TestRuleSource

        and:
        container.apply("not-a-plugin")

        then:
        0 * target._
        container.processedRuleContainers.count(TestRuleSource) == 1

        and:
        container.contains(TestRuleSource)
    }

    def "delegates to plugin container and extracts rules if applied class applied implements Plugin"() {
        when:
        target.getPlugins() >> pluginContainer

        and:
        container.apply(TestPlugin)

        then:
        1 * pluginContainer.apply(TestPlugin)
        container.processedRuleContainers.count(TestPlugin) == 1

        and:
        container.contains(TestPlugin)
    }

    def "extracts rules if applied class does not implement Plugin"() {
        when:
        container.apply(TestRuleSource)

        then:
        0 * target._
        container.processedRuleContainers.count(TestRuleSource) == 1

        and:
        container.contains(TestRuleSource)
    }

    def "applying a plugin that does not implement Plugin is idempotent"() {
        when:
        container.apply(TestRuleSource)

        then:

        container.processedRuleContainers.count(TestRuleSource) == 1

        when:
        container.apply(TestRuleSource)

        then:
        container.processedRuleContainers.count(TestRuleSource) == 1
    }

    def "applying a plugin that implements Plugin is idempotent"() {
        when:
        target.plugins >> pluginContainer
        pluginContainer.apply(_) >> { container.apply(it) }

        and:
        container.apply(TestPlugin)

        then:
        notThrown(StackOverflowError)
        container.processedRuleContainers.count(TestPlugin) == 1
    }

    def "does not allow to apply a type that is neither a plugin or a rule source"() {
        when:
        container.apply(String)

        then:
        IllegalArgumentException e = thrown()
        e.message == "'${String.name}' is neither a plugin or a rule source and cannot be applied."
    }
}
