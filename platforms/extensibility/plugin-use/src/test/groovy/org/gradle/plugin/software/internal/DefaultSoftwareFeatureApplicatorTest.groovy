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

package org.gradle.plugin.software.internal

import org.gradle.api.Plugin
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.InternalProblems
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.bean.PropertyWalker
import spock.lang.Specification

class DefaultSoftwareFeatureApplicatorTest extends Specification {
    def target = Mock(ProjectInternal)
    def modelDefaultsApplicator = Mock(ModelDefaultsApplicator)
    def inspectionScheme = Mock(InspectionScheme)
    def problems = Mock(InternalProblems) {
        getAdditionalDataBuilderFactory() >> new AdditionalDataBuilderFactory()
    }
    def pluginManager = Mock(PluginManagerInternal)
    def applicator = new DefaultSoftwareFeatureApplicator(modelDefaultsApplicator, inspectionScheme, problems, pluginManager)
    def plugin = Mock(Plugin)
    def plugins = Mock(PluginContainer)
    def propertyWalker = Mock(PropertyWalker)
    def propertyValue = Mock(PropertyValue)
    def softwareType = Mock(SoftwareType)
    def extensions = Mock(ExtensionContainerInternal)
    SoftwareTypeImplementation<Foo> softwareTypeImplementation = Mock(SoftwareTypeImplementation)
    def foo = new Foo()

    def "adds software types as extensions when software type plugin is applied"() {
        when:
        def returned = applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        _ * softwareTypeImplementation.pluginClass >> plugin.class
        1 * pluginManager.pluginContainer >> plugins
        1 * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        _ * target.getExtensions() >> extensions
        1 * softwareType.modelPublicType() >> Foo.class
        1 * softwareType.name() >> "foo"
        1 * propertyValue.call() >> foo
        1 * extensions.add(Foo.class, "foo", foo)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, plugin, softwareTypeImplementation)
        _ * softwareTypeImplementation.softwareType >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        returned == foo
    }

    def "only adds software types as extensions once when software type plugin is applied multiple times"() {
        when:
        def first = applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        _ * softwareTypeImplementation.pluginClass >> plugin.class
        1 * pluginManager.pluginContainer >> plugins
        1 * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        _ * target.getExtensions() >> extensions
        1 * softwareType.modelPublicType() >> Foo.class
        1 * softwareType.name() >> "foo"
        1 * propertyValue.call() >> foo
        1 * extensions.add(Foo.class, "foo", foo)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, plugin, softwareTypeImplementation)
        _ * softwareTypeImplementation.softwareType >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        first == foo

        when:
        def second = applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        _ * target.getExtensions() >> extensions
        _ * softwareTypeImplementation.softwareType >> "foo"
        1 * extensions.getByName("foo") >> foo
        0 * _

        and:
        second == first
        second == foo
    }

    def "does not add software type as extension when registration is disabled"() {
        def plugin = Mock(Plugin)

        when:
        def returned = applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        _ * extensions.findByName("foo") >> foo

        and:
        _ * softwareTypeImplementation.pluginClass >> plugin.class
        1 * pluginManager.pluginContainer >> plugins
        1 * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        1 * propertyValue.call() >> foo
        _ * target.getExtensions() >> extensions
        _ * softwareType.name() >> "foo"
        0 * extensions.add(_, _, _)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, plugin, softwareTypeImplementation)
        _ * softwareTypeImplementation.softwareType >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        returned == foo
    }

    def "throws sensible error when registration is disabled but no extension is registered"() {
        def plugin = Mock(Plugin)

        when:
        applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        1 * extensions.findByName("foo") >> null

        and:
        _ * softwareTypeImplementation.pluginClass >> plugin.class
        1 * pluginManager.pluginContainer >> plugins
        1 * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        1 * propertyValue.call() >> foo
        _ * target.getExtensions() >> extensions
        _ * softwareType.name() >> "foo"
        0 * extensions.add(_, _, _)

        and:
        def e = thrown(DefaultMultiCauseException)
        e.causes.size() == 1
        e.causes.find { it.message.contains("property 'foo' has @SoftwareType annotation with 'disableModelManagement' set to true, but no extension with name 'foo' was registered")}
    }

    def "throws sensible error when registration is disabled but extension is different than the property"() {
        def plugin = Mock(Plugin)

        when:
        applicator.applyFeatureTo(target, softwareTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        1 * extensions.findByName("foo") >> new Foo()

        and:
        _ * softwareTypeImplementation.pluginClass >> plugin.class
        1 * pluginManager.pluginContainer >> plugins
        1 * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        1 * propertyValue.call() >> foo
        _ * target.getExtensions() >> extensions
        _ * softwareType.name() >> "foo"
        0 * extensions.add(_, _, _)

        and:
        def e = thrown(DefaultMultiCauseException)
        e.causes.size() == 1
        e.causes.find { it.message.contains("property 'foo' has @SoftwareType annotation with 'disableModelManagement' set to true, but the extension with name 'foo' does not match the value of the property")}
    }

    private static class Foo {}
}
