/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import spock.lang.Specification

import static org.gradle.util.TestUtil.instantiatorFactory

class SettingsFactoryTest extends Specification {

    void createSettings() {
        given:
        def settingsDir = new File("settingsDir")
        def scriptSource = Mock(ScriptSource)
        def gradleProperties = Mock(GradleProperties)
        def startParameter = new StartParameter()
        def serviceRegistryFactory = Mock(ServiceRegistryFactory)
        def settingsServices = Mock(ServiceRegistry)
        def fileResolver = Mock(FileResolver)
        def scriptPluginFactory = Mock(ScriptPluginFactory)
        def scriptHandlerFactory = Mock(ScriptHandlerFactory)
        def projectDescriptorRegistry = Mock(ProjectDescriptorRegistry)
        def scope = Mock(ClassLoaderScope)

        1 * serviceRegistryFactory.createFor(_ as Settings) >> settingsServices
        1 * settingsServices.get(FileResolver) >> fileResolver
        1 * settingsServices.get(ScriptPluginFactory) >> scriptPluginFactory
        1 * settingsServices.get(ScriptHandlerFactory) >> scriptHandlerFactory
        1 * settingsServices.get(ProjectDescriptorRegistry) >> projectDescriptorRegistry
        1 * settingsServices.get(InstantiatorFactory) >> Stub(InstantiatorFactory)
        1 * settingsServices.get(DependencyResolutionManagementInternal) >> Stub(DependencyResolutionManagementInternal)
        1 * projectDescriptorRegistry.addProject(_ as DefaultProjectDescriptor)
        1 * scriptHandlerFactory.create(scriptSource, _ as ClassLoaderScope) >> Mock(ScriptHandlerInternal)
        1 * scope.createChild(_) >> scope
        1 * gradleProperties.mergeProperties(_) >> ['myKey': 'myValue']

        when:
        SettingsFactory settingsFactory = new SettingsFactory(instantiatorFactory().decorateLenient(), serviceRegistryFactory, scriptHandlerFactory)
        GradleInternal gradle = Mock(GradleInternal)

        DefaultSettings settings = (DefaultSettings) settingsFactory.createSettings(gradle,
            settingsDir, scriptSource, gradleProperties, startParameter, scope)

        then:
        gradle.is(settings.gradle)
        projectDescriptorRegistry.is(settings.projectDescriptorRegistry)

        and: 'Gradle properties are visible through the settings object'
        settings.myKey == 'myValue'

        and: 'Gradle properties are visible through the ext extension'
        settings.ext.myKey == 'myValue'

        and: 'Gradle properties are NOT visible through the Groovy properties builtin'
        'myKey' !in settings.properties

        and:
        settingsDir.is settings.getSettingsDir()
        scriptSource.is settings.getSettingsScript()
        startParameter.is settings.getStartParameter()
    }
}
