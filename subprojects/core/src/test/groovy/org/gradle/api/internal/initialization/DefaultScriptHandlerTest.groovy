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

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.ConfigureUtil
import spock.lang.Specification

class DefaultScriptHandlerTest extends Specification {
    def repositoryHandler = Mock(RepositoryHandler)
    def dependencyHandler = Mock(DependencyHandler)
    def configurationContainer = Mock(ConfigurationContainer)
    def configuration = Stub(Configuration)
    def scriptSource = Stub(ScriptSource)
    def baseClassLoader = new ClassLoader() {}
    def classLoaderScope = Stub(ClassLoaderScope) {
        getScopeClassLoader() >> baseClassLoader
    }

    def "adds classpath configuration"() {
        when:
        new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, new ScriptHandlerClassLoaderFactory(scriptSource, classLoaderScope))

        then:
        1 * configurationContainer.create('classpath')
    }

    def "can configure repositories"() {
        def handler = handler()
        def configure = {
            mavenCentral()
        }

        when:
        handler.repositories(configure)

        then:
        1 * repositoryHandler.configure(configure) >> { ConfigureUtil.configure(configure, repositoryHandler, false) }
        1 * repositoryHandler.mavenCentral()
    }

    def "can configure dependencies"() {
        def handler = handler()

        when:
        handler.dependencies {
            add('config', 'dep')
        }

        then:
        1 * dependencyHandler.add('config', 'dep')
    }

    private DefaultScriptHandler handler() {
        1 * configurationContainer.create('classpath') >> configuration
        return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, new ScriptHandlerClassLoaderFactory(scriptSource, classLoaderScope))
    }
}