/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.buildoption.FeatureFlags
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultSettingsCommonTest extends Specification {

    File settingsDir
    StartParameter startParameter
    ClassLoaderScope rootClassLoaderScope = Mock(ClassLoaderScope)
    ClassLoaderScope classLoaderScope = Mock(ClassLoaderScope)
    ScriptSource scriptSourceMock = Mock(ScriptSource)
    GradleInternal gradleMock = Mock(GradleInternal)
    ProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    ServiceRegistryFactory serviceRegistryFactory
    FileResolver fileResolver = Mock(FileResolver)
    ScriptPluginFactory scriptPluginFactory = Mock(ScriptPluginFactory)
    ScriptHandlerFactory scriptHandlerFactory = Mock(ScriptHandlerFactory)
    ScriptHandler settingsScriptHandler = Mock(ScriptHandler)
    DefaultPluginManager pluginManager = Mock(DefaultPluginManager)
    FeatureFlags previews = Mock(FeatureFlags)
    DefaultSettings settings

    def createSettings(String path = '/somepath/root') {
        settingsDir = new File(path).absoluteFile
        startParameter = new StartParameter(currentDir: new File(settingsDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))

        fileResolver.resolve(_) >> { args -> args[0].canonicalFile }

        def settingsServices = Mock(ServiceRegistry)
        settingsServices.get(FileResolver) >> fileResolver
        settingsServices.get(ScriptPluginFactory) >> scriptPluginFactory
        settingsServices.get(ScriptHandlerFactory) >> scriptHandlerFactory
        settingsServices.get(ProjectDescriptorRegistry) >> projectDescriptorRegistry
        settingsServices.get(FeatureFlags) >> previews
        settingsServices.get(DefaultPluginManager) >>> [pluginManager, null]
        settingsServices.get(InstantiatorFactory) >> Stub(InstantiatorFactory)
        settingsServices.get(DependencyResolutionManagementInternal) >> Stub(DependencyResolutionManagementInternal)

        serviceRegistryFactory = Mock(ServiceRegistryFactory) {
            1 * createFor(_) >> settingsServices
        }

        def instantiator = TestUtil.instantiatorFactory().decorateLenient()
        settings = instantiator.newInstance(DefaultSettings, serviceRegistryFactory,
            gradleMock, classLoaderScope, rootClassLoaderScope, settingsScriptHandler,
            settingsDir, scriptSourceMock, startParameter)
    }
}
