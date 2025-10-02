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
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.plugins.BuildModel
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.software.SoftwareType
import org.gradle.api.internal.tasks.properties.InspectionScheme
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.extensibility.ExtensibleDynamicObject
import org.gradle.internal.metaobject.DynamicInvokeResult
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.bean.PropertyWalker
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultProjectFeatureApplicatorTest extends Specification {
    def targetChildrenDefinitions = new LinkedHashMap()
    def targetFeatureDefinitionContext = Mock(ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext) {
        it.childrenDefinitions() >> targetChildrenDefinitions
        _ * it.getOrAddChildDefinition(_, _) >> { args ->
            if (targetChildrenDefinitions.containsKey(args[0])) {
                return new ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult(false, targetChildrenDefinitions.get(args[0]))
            } else {
                def definition = args[1].get()
                targetChildrenDefinitions.put(args[0], definition)
                return new ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult(true, definition)
            }
        }
    }
    def targetDynamicObject = Mock(ExtensibleDynamicObject) {
        tryInvokeMethod(ProjectFeaturesDynamicObject.CONTEXT_METHOD_NAME, _ as Object[]) >>
            DynamicInvokeResult.found(targetFeatureDefinitionContext)
    }
    def target = Mock(DefinitionWithExtensions) {
        _ * it.asDynamicObject >> targetDynamicObject
    }

    def modelDefaultsApplicator = Mock(ModelDefaultsApplicator)
    def inspectionScheme = Mock(InspectionScheme)
    def problems = TestUtil.problemsService()
    def pluginManager = Mock(PluginManagerInternal)
    def classLoaderScope = Mock(ClassLoaderScope) {
        _ * it.getLocalClassLoader() >> getClass().classLoader
    }
    def objectFactory = Mock(ObjectFactory)
    def projectFeatureRegistry = Mock(ProjectFeatureRegistry)
    def applicator = new DefaultProjectFeatureApplicator(projectFeatureRegistry, modelDefaultsApplicator, inspectionScheme, problems, pluginManager, classLoaderScope, objectFactory)
    def plugin = Mock(Plugin)
    def plugins = Mock(PluginContainer)
    def propertyWalker = Mock(PropertyWalker)
    def propertyValue = Mock(PropertyValue)
    def softwareType = Mock(SoftwareType)
    def extensions = Mock(ExtensionContainerInternal)
    def legacyProjectTypeImplementation = Mock(LegacyProjectTypeImplementation)
    def foo = Mock(Foo) {
        _ * it.asDynamicObject >> Mock(ExtensibleDynamicObject)
    }

    def "adds project types as extensions when project type plugin is applied"() {
        when:
        def returned = applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        _ * legacyProjectTypeImplementation.pluginClass >> plugin.class
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        _ * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args ->
            args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType)
        }
        _ * target.extensions >> extensions
        1 * softwareType.modelPublicType() >> Foo.class
        1 * softwareType.name() >> "foo"
        1 * propertyValue.call() >> foo
        1 * extensions.add(Foo.class, "foo", foo)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, _, _, plugin, legacyProjectTypeImplementation)
        _ * legacyProjectTypeImplementation.featureName >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        returned == foo
    }

    def "only adds project types as extensions once when project type plugin is applied multiple times"() {
        when:
        def first = applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        _ * legacyProjectTypeImplementation.pluginClass >> plugin.class
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        _ * target.getExtensions() >> extensions
        1 * softwareType.modelPublicType() >> Foo.class
        1 * softwareType.name() >> "foo"
        1 * propertyValue.call() >> foo
        1 * extensions.add(Foo.class, "foo", foo)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, _, _, plugin, legacyProjectTypeImplementation)
        _ * legacyProjectTypeImplementation.featureName >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        first == foo

        when:
        def second = applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        _ * legacyProjectTypeImplementation.featureName >> "foo"
        1 * target.asDynamicObject >> targetDynamicObject
        1 * targetDynamicObject.tryInvokeMethod(ProjectFeaturesDynamicObject.CONTEXT_METHOD_NAME, _) >> DynamicInvokeResult.found(targetFeatureDefinitionContext)
        1 * targetFeatureDefinitionContext.getOrAddChildDefinition(legacyProjectTypeImplementation, _) >>
            new ProjectFeatureSupportInternal.ProjectFeatureDefinitionContext.ChildDefinitionAdditionResult(false, foo)
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        0 * _

        and:
        second == first
        second == foo
    }

    def "does not add project type as extension when registration is disabled"() {
        def plugin = Mock(Plugin)

        when:
        def returned = applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        _ * extensions.findByName("foo") >> foo

        and:
        _ * legacyProjectTypeImplementation.pluginClass >> plugin.class
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        1 * inspectionScheme.getPropertyWalker() >> propertyWalker
        1 * propertyWalker.visitProperties(plugin, _, _) >> { args -> args[2].visitSoftwareTypeProperty("foo", propertyValue, Foo.class, softwareType) }
        1 * propertyValue.call() >> foo
        _ * target.getExtensions() >> extensions
        _ * softwareType.name() >> "foo"
        0 * extensions.add(_, _, _)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, _, _, plugin, legacyProjectTypeImplementation)
        _ * legacyProjectTypeImplementation.featureName >> "foo"
        1 * extensions.getByName("foo") >> foo

        and:
        returned == foo
    }

    def "throws sensible error when registration is disabled but no extension is registered"() {
        def plugin = Mock(Plugin)

        when:
        applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        1 * extensions.findByName("foo") >> null

        and:
        _ * legacyProjectTypeImplementation.pluginClass >> plugin.class
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
        applicator.applyFeatureTo(target as DynamicObjectAware, legacyProjectTypeImplementation)

        then:
        1 * softwareType.disableModelManagement() >> true
        1 * extensions.findByName("foo") >> Mock(Foo) { it.asDynamicObject >> Mock(ExtensibleDynamicObject) }

        and:
        _ * legacyProjectTypeImplementation.pluginClass >> plugin.class
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

    private interface DefinitionWithExtensions extends DynamicObjectAware, ExtensionAware {}

    private interface Foo extends Definition<Bar>, DynamicObjectAware {}
    private static class Bar implements BuildModel {}
}
