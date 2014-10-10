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

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.api.specs.Spec
import org.gradle.testfixtures.CustomRuleSource
import spock.lang.Specification

class DefaultAppliedPluginsTest extends Specification {

    def container = new AppliedPluginTestContainer()
    def pluginRegistry = Mock(PluginRegistry)
    def appliedPlugins = new DefaultAppliedPlugins(container, pluginRegistry)

    static class AppliedPluginTestContainer implements AppliedPluginContainer {

        private final DomainObjectSet<Class<? extends Object>> appliedPlugins = new DefaultDomainObjectSet<Class<?>>(Class.class.getClass() as Class<? extends Class<?>>); ;

        void apply(Class<?> pluginClass) {
            appliedPlugins.add(pluginClass)
        }

        @Override
        boolean contains(Class<?> pluginClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        DomainObjectCollection<Class<?>> matching(Spec<? super Class<?>> spec) {
            appliedPlugins.matching(spec)
        }
    }

    def "no failures for unknown plugins that are not applied"() {
        given:
        def action = Mock(Action)
        pluginRegistry.getTypeForId("foo") >> { throw new UnknownPluginException("unknown plugin 'foo''") }

        expect:
        !appliedPlugins.contains("foo")
        appliedPlugins.findPlugin("foo") == null

        when:
        appliedPlugins.withPlugin("foo", action)

        then:
        0 * action._
    }

    def "plugins that are in the registry but are not applied"() {
        given:
        def action = Mock(Action)
        pluginRegistry.getTypeForId("org.gradle.custom-rule-source") >> CustomRuleSource

        expect:
        !appliedPlugins.contains("org.gradle.custom-rule-source")
        appliedPlugins.findPlugin("org.gradle.custom-rule-source") == null

        when:
        appliedPlugins.withPlugin("org.gradle.custom-rule-source", action)

        then:
        0 * action._
    }

    def "applied plugins that are available in the plugin registry"() {
        given:
        def action = Mock(Action)
        container.apply(CustomRuleSource)
        pluginRegistry.getTypeForId("org.gradle.custom-rule-source") >> CustomRuleSource

        expect:
        appliedPlugins.contains("org.gradle.custom-rule-source")
        appliedPlugins.findPlugin("org.gradle.custom-rule-source") == CustomRuleSource

        when:
        appliedPlugins.withPlugin("org.gradle.custom-rule-source", action)

        then:
        1 * action.execute(CustomRuleSource)
    }

    def "applied plugins that are not available in the plugin registry"() {
        given:
        def action = Mock(Action)
        container.apply(CustomRuleSource)
        pluginRegistry.getTypeForId("org.gradle.custom-rule-source") >> { throw new UnknownPluginException("unknown plugin 'custom-rule-source'") }

        expect:
        appliedPlugins.contains("org.gradle.custom-rule-source")
        appliedPlugins.findPlugin("org.gradle.custom-rule-source") == CustomRuleSource

        when:
        appliedPlugins.withPlugin("org.gradle.custom-rule-source", action)

        then:
        1 * action.execute(CustomRuleSource)
    }

    def "plugins that are not in the registry and are applied after registering an action trigger the registered action"() {
        given:
        def action = Mock(Action)
        pluginRegistry.getTypeForId("org.gradle.custom-rule-source") >> { throw new UnknownPluginException("unknown plugin 'custom-rule-source'") }

        when:
        appliedPlugins.withPlugin("org.gradle.custom-rule-source", action)

        then:
        0 * action._

        when:
        container.apply(CustomRuleSource)

        then:
        1 * action.execute(CustomRuleSource)
    }

    def "plugins that are in the registry and are applied after registering an action trigger the registered action"() {
        given:
        def action = Mock(Action)
        pluginRegistry.getTypeForId("org.gradle.custom-rule-source") >> CustomRuleSource

        when:
        appliedPlugins.withPlugin("org.gradle.custom-rule-source", action)

        then:
        0 * action._

        when:
        container.apply(CustomRuleSource)

        then:
        1 * action.execute(CustomRuleSource)
    }
}
