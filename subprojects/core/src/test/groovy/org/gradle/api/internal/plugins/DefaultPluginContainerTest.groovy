/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.api.internal.project.TestPlugin1
import org.gradle.api.internal.project.TestPlugin2
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

public class DefaultPluginContainerTest extends Specification {

    def project = TestUtil.createRootProject()
    def pluginRegistry = Mock(PluginRegistry)
    @Subject container = new DefaultPluginContainer(pluginRegistry, project)

    def "offers plugin management via plugin id"() {
        def plugin = new TestPlugin1()
        pluginRegistry.getTypeForId("plugin") >> TestPlugin1
        pluginRegistry.loadPlugin(TestPlugin1) >> plugin
        pluginRegistry.loadPlugin(TestPlugin2) >> new TestPlugin2()

        when: def p = container.apply(TestPlugin1)
        then:
        p.is(plugin)
        p.is(container.apply("plugin"))
        p.is(container.apply(TestPlugin1))

        p.is(container.findPlugin(TestPlugin1))
        p.is(container.findPlugin("plugin"))

        !container.findPlugin(UnknownPlugin)
        !container.findPlugin("unknown")

        container.hasPlugin("plugin")
        container.hasPlugin(TestPlugin1)

        !container.hasPlugin("unknown")
        !container.hasPlugin(UnknownPlugin)
    }

    private class UnknownPlugin implements Plugin<Project> {
        void apply(Project project) {}
    }

    def "offers plugin management via plugin type"() {
        def plugin = new TestPlugin1()
        pluginRegistry.loadPlugin(TestPlugin1) >> plugin
        pluginRegistry.loadPlugin(TestPlugin2) >> new TestPlugin2()

        when: def p = container.apply(TestPlugin1)
        then:
        p.is(plugin)
        p.is(container.apply(TestPlugin1))
        p.is(container.findPlugin(TestPlugin1))
        container.hasPlugin(TestPlugin1)

        !p.is(container.findPlugin(TestPlugin2))
        !container.hasPlugin(TestPlugin2)
    }

    def "does not find plugin by unknown id"() {
        pluginRegistry.getTypeForId("x") >> { throw new UnknownPluginException("x") }

        expect:
        !container.hasPlugin("x")
        !container.findPlugin("x")
    }

    def "fails when getting unknown plugin"() {
        when: container.getPlugin("unknown")
        then: thrown(UnknownPluginException)
    }

    def "fails when getting plugin of unknown type"() {
        when: container.getPlugin(TestPlugin1)
        then: thrown(UnknownPluginException)
    }
}
