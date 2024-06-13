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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.plugin.software.internal.ConventionHandler
import org.gradle.plugin.software.internal.SoftwareTypeImplementation
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import spock.lang.Specification

class ApplySoftwareTypeConventionsPluginTargetTest extends Specification {
    def target = Mock(ProjectInternal)
    def delegate = Mock(PluginTarget)
    def softwareTypeRegistry = Mock(SoftwareTypeRegistry)
    def softwareTypeImplementation = Mock(SoftwareTypeImplementation)
    def handler1 = Mock(ConventionHandler)
    def handler2 = Mock(ConventionHandler)
    def pluginTarget = new ApplySoftwareTypeConventionsPluginTarget(target, delegate, softwareTypeRegistry, [handler1, handler2])

    def "invokes handlers for software types when software type plugin is applied"() {
        def plugin = Mock(Plugin)

        when:
        pluginTarget.applyImperative(null, plugin)

        then:
        1 * delegate.applyImperative(null, plugin)

        then:
        1 * softwareTypeRegistry.implementationFor(_) >> Optional.of(softwareTypeImplementation)
        2 * softwareTypeImplementation.getSoftwareType() >> "foo"
        1 * handler1.apply(target, "foo")
        1 * handler2.apply(target, "foo")
    }

    def "does not invoke handlers when a plugin is applied that is not a software type"() {
        def plugin = Mock(Plugin)

        when:
        pluginTarget.applyImperative(null, plugin)

        then:
        1 * delegate.applyImperative(null, plugin)

        then:
        1 * softwareTypeRegistry.implementationFor(_) >> Optional.empty()
        0 * handler1.apply(_, _)
        0 * handler2.apply(_, _)
    }
}
