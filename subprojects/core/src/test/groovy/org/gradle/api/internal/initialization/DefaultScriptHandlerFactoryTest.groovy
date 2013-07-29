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
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.groovy.scripts.ScriptSource

import spock.lang.Specification
import org.gradle.internal.classloader.MutableURLClassLoader

class DefaultScriptHandlerFactoryTest extends Specification {
    private final DependencyMetaDataProvider metaDataProvider = Mock()
    private final ClassLoader parentClassLoader = new ClassLoader() {}
    private final RepositoryHandler repositoryHandler = Mock()
    private final ConfigurationContainerInternal configurationContainer = Mock()
    private final FileResolver fileResolver = Mock()
    private final DependencyManagementServices dependencyManagementServices = Mock()
    private final DefaultScriptHandlerFactory factory = new DefaultScriptHandlerFactory(dependencyManagementServices, fileResolver, metaDataProvider)

    def createsScriptHandler() {
        ScriptSource script = scriptSource()
        expectConfigContainerCreated()

        when:
        def handler = factory.create(script, parentClassLoader)

        then:
        handler instanceof DefaultScriptHandler
        handler.classLoader instanceof MutableURLClassLoader
        handler.classLoader.parent == parentClassLoader
    }

    def reusesClassLoaderForGivenScriptClassAndParentClassLoader() {
        ScriptSource script = scriptSource('script')
        ScriptSource other = scriptSource('script')
        expectConfigContainerCreated()

        when:
        def handler1 = factory.create(script, parentClassLoader)
        def handler2 = factory.create(other, parentClassLoader)

        then:
        handler1.classLoader == handler2.classLoader
        handler2 instanceof NoClassLoaderUpdateScriptHandler
    }

    def doesNotReuseClassLoaderForDifferentScriptClass() {
        ScriptSource script = scriptSource('script')
        ScriptSource other = scriptSource('other')
        expectConfigContainerCreated()

        when:
        def handler1 = factory.create(script, parentClassLoader)
        def handler2 = factory.create(other, parentClassLoader)

        then:
        handler1.classLoader != handler2.classLoader
        handler2 instanceof DefaultScriptHandler
    }

    private def expectConfigContainerCreated() {
        DependencyResolutionServices dependencyResolutionServices = Mock()
        _ * dependencyManagementServices.create(fileResolver, metaDataProvider, _, _) >> dependencyResolutionServices
        _ * dependencyResolutionServices.resolveRepositoryHandler >> repositoryHandler
        _ * dependencyResolutionServices.configurationContainer >> configurationContainer
    }

    private def scriptSource(String className = 'script') {
        ScriptSource script = Mock()
        _ * script.className >> className
        script
    }
}
