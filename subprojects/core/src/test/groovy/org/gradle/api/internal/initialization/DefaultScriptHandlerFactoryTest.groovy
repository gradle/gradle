/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.initialization

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.groovy.scripts.ScriptSource
import spock.lang.Specification

class DefaultScriptHandlerFactoryTest extends Specification {
    private final DependencyMetaDataProvider metaDataProvider = Mock()
    private final ScriptCompileScope parentScope = Stub() {
        getScriptCompileClassLoader() >> Stub(ClassLoader)
    }
    private final RepositoryHandler repositoryHandler = Mock()
    private final ConfigurationContainerInternal configurationContainer = Mock()
    private final FileResolver fileResolver = Mock()
    private final DependencyManagementServices dependencyManagementServices = Mock()
    private final DefaultScriptHandlerFactory factory = new DefaultScriptHandlerFactory(dependencyManagementServices, fileResolver, metaDataProvider)

    def createsScriptHandler() {
        ScriptSource script = scriptSource()
        expectConfigContainerCreated()

        when:
        def handler = factory.create(script, parentScope)

        then:
        handler instanceof DefaultScriptHandler
    }

    def reusesClassLoaderForGivenScriptClassAndParentScope() {
        ScriptSource script = scriptSource('script')
        ScriptSource other = scriptSource('script')
        expectConfigContainerCreated()

        when:
        def handler1 = factory.create(script, parentScope)
        handler1.updateClassPath()
        def handler2 = factory.create(other, parentScope)

        then:
        handler2 instanceof NoClassLoaderUpdateScriptHandler
        handler1.baseCompilationClassLoader == handler2.baseCompilationClassLoader
        handler1.scriptCompileClassLoader == handler2.scriptCompileClassLoader
    }

    def doesNotReuseClassLoaderForDifferentScriptClass() {
        ScriptSource script = scriptSource('script')
        ScriptSource other = scriptSource('other')
        expectConfigContainerCreated()

        when:
        factory.create(script, parentScope)
        def handler2 = factory.create(other, parentScope)

        then:
        handler2 instanceof DefaultScriptHandler
    }

    private def expectConfigContainerCreated() {
        DependencyResolutionServices dependencyResolutionServices = Mock()
        _ * dependencyManagementServices.create(fileResolver, metaDataProvider, _, _) >> dependencyResolutionServices
        _ * dependencyResolutionServices.resolveRepositoryHandler >> repositoryHandler
        _ * dependencyResolutionServices.configurationContainer >> configurationContainer
        _ * configurationContainer.create(_) >> Stub(ConfigurationInternal)
    }

    private def scriptSource(String className = 'script') {
        ScriptSource script = Mock()
        _ * script.className >> className
        script
    }
}
