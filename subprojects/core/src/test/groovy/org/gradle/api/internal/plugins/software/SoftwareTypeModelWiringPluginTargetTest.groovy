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

package org.gradle.api.internal.plugins.software

import org.gradle.api.Plugin
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.PluginTarget
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.bean.PropertyPairWalker
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.plugin.software.internal.SoftwareTypeRegistry
import spock.lang.Specification

class SoftwareTypeModelWiringPluginTargetTest extends Specification {
    def target = Mock(ProjectInternal)
    def delegate = Mock(PluginTarget)
    def inspectionScheme = Mock(InspectionScheme)
    def softwareTypeRegistry = Mock(SoftwareTypeRegistry)
    def pluginTarget = new SoftwareTypeModelWiringPluginTarget(target, delegate, inspectionScheme, softwareTypeRegistry)

    def "adds software types as extensions and applies conventions when software type plugin is applied"() {
        def plugin = Mock(Plugin)
        def propertyWalker = Mock(PropertyWalker)
        def propertyPairWalker = Mock(PropertyPairWalker)
        def propertyValue = Mock(PropertyValue)
        def softwareType = Mock(SoftwareType)
        def extensions = Mock(ExtensionContainerInternal)
        def gradle = Mock(GradleInternal)
        def settings = Mock(SettingsInternal)
        def settingsExtensions = Mock(ExtensionContainer)
        def foo = new Foo()
        def barProperty = Mock(Property)
        def barConventionProperty = Mock(Property)

        when:
        pluginTarget.applyImperative(null, plugin)

        then:
        1 * softwareTypeRegistry.isRegistered(_) >> true
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, softwareType) }
        1 * target.getExtensions() >> extensions
        2 * softwareType.modelPublicType() >> Foo.class
        2 * softwareType.name() >> "foo"
        1 * propertyValue.call() >> foo
        1 * extensions.add(Foo.class, "foo", foo)

        and:
        1 * delegate.applyImperative(null, plugin)

        and:
        1 * target.getGradle() >> gradle
        1 * gradle.getSettings() >> settings
        1 * settings.getExtensions() >> settingsExtensions
        1 * settingsExtensions.getByName(_) >> new Foo()
        1 * inspectionScheme.getPropertyPairWalker() >> propertyPairWalker
        1 * propertyPairWalker.visitPropertyPairs(_, _, _, _) >> { args -> args[3].visitPropertyTypePair(barProperty, barConventionProperty) }
        1 * barConventionProperty.isPresent() >> true
        1 * barProperty.convention(barConventionProperty)
    }

    def "does not add software types for plugins that are not registered"() {
        def plugin = Mock(Plugin)

        when:
        pluginTarget.applyImperative(null, plugin)

        then:
        1 * softwareTypeRegistry.isRegistered(_) >> false

        and:
        1 * delegate.applyImperative(null, plugin)
    }

    def "passes rule targets to delegate only"() {
        when:
        pluginTarget.applyRules(null, Rule.class)

        then:
        1 * delegate.applyRules(null, Rule.class)
        0 * _
    }

    private static class Foo {}
    private static class Rule {}
}
