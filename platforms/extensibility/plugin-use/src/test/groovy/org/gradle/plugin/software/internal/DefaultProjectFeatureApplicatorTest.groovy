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
import org.gradle.api.internal.plugins.Definition
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.ProjectFeatureApplyAction
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.PluginContainer
import org.gradle.internal.extensibility.ExtensibleDynamicObject
import org.gradle.internal.metaobject.DynamicInvokeResult
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
    def pluginManager = Mock(PluginManagerInternal)
    def classLoaderScope = Mock(ClassLoaderScope) {
        _ * it.getLocalClassLoader() >> getClass().classLoader
    }
    def objectFactory = Mock(ObjectFactory)
    def projectFeatureRegistry = Mock(ProjectFeatureDeclarations)
    def applicator = new DefaultProjectFeatureApplicator(projectFeatureRegistry, modelDefaultsApplicator, pluginManager, classLoaderScope, objectFactory)
    def plugin = Mock(Plugin)
    def plugins = Mock(PluginContainer)
    def boundProjectTypeImplementation = Mock(BoundProjectFeatureImplementation)
    def foo = Mock(Foo) {
        _ * it.asDynamicObject >> Mock(ExtensibleDynamicObject)
    }

    def "applies plugin and defaults when project type is applied"() {
        when:
        def returned = applicator.applyFeatureTo(target as DynamicObjectAware, boundProjectTypeImplementation)

        then:
        _ * boundProjectTypeImplementation.pluginClass >> plugin.class
        _ * boundProjectTypeImplementation.definitionImplementationType >> Foo
        _ * boundProjectTypeImplementation.buildModelImplementationType >> Bar
        _ * boundProjectTypeImplementation.bindingTransform >> Mock(ProjectFeatureApplyAction)
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        1 * pluginManager.apply(plugin.class)
        1 * modelDefaultsApplicator.applyDefaultsTo(target, _, _, plugin, boundProjectTypeImplementation)
        1 * objectFactory.newInstance(Foo) >> foo

        and:
        returned == foo
    }

    def "throws exception when multiple project types are applied to a project"() {
        def projectDynamicObject = Mock(ExtensibleDynamicObject) {
            tryInvokeMethod(ProjectFeaturesDynamicObject.CONTEXT_METHOD_NAME, _ as Object[]) >>
                DynamicInvokeResult.found(targetFeatureDefinitionContext)
        }
        def project = Mock(DynamicAwareProjectMockType) {
            _ * it.asDynamicObject >> projectDynamicObject
        }
        def secondProjectTypeImplementation = Mock(BoundProjectFeatureImplementation)

        when:
        def returned = applicator.applyFeatureTo(project as DynamicObjectAware, boundProjectTypeImplementation)

        then:
        _ * boundProjectTypeImplementation.pluginClass >> plugin.class
        _ * boundProjectTypeImplementation.definitionImplementationType >> Foo
        _ * boundProjectTypeImplementation.buildModelImplementationType >> Bar
        _ * boundProjectTypeImplementation.bindingTransform >> Mock(ProjectFeatureApplyAction)
        _ * pluginManager.pluginContainer >> plugins
        _ * plugins.getPlugin(plugin.class) >> plugin
        1 * pluginManager.apply(plugin.class)
        1 * modelDefaultsApplicator.applyDefaultsTo(project, _, _, plugin, boundProjectTypeImplementation)
        1 * objectFactory.newInstance(Foo) >> foo

        and:
        returned == foo

        when:
        applicator.applyFeatureTo(project as DynamicObjectAware, secondProjectTypeImplementation)

        then:
        thrown(IllegalStateException)
    }

    private interface DefinitionWithExtensions extends DynamicObjectAware, ExtensionAware {}
    private interface DynamicAwareProjectMockType extends ProjectInternal, DynamicObjectAware {}
    private interface Foo extends Definition<Bar>, DynamicObjectAware {}
    private static class Bar implements BuildModel {}
}
