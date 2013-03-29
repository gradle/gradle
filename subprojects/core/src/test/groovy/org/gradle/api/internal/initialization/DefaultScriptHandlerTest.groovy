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
import org.gradle.util.WrapUtil
import org.gradle.util.MutableURLClassLoader
import org.gradle.util.ConfigureUtil

import spock.lang.Specification

class DefaultScriptHandlerTest extends Specification {
    RepositoryHandler repositoryHandler = Mock()
    DependencyHandler dependencyHandler = Mock()
    ConfigurationContainer configurationContainer = Mock()
    Configuration configuration = Mock()
    ScriptSource scriptSource = Mock()
    MutableURLClassLoader classLoader = Mock()

    def "adds classpath configuration"() {
        when:
        new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, classLoader)

        then:
        1 * configurationContainer.create('classpath')
    }

    def "creates a class loader and adds contents of classpath configuration"() {
        def handler = handler()
        def classLoader = handler.classLoader

        expect:
        classLoader.is this.classLoader

        def file1 = new File('a')
        def file2 = new File('b')

        when:
        handler.updateClassPath()

        then:
        1 * configuration.getFiles() >> WrapUtil.toSet(file1, file2)
        1 * classLoader.addURL(file1.toURI().toURL())
        1 * classLoader.addURL(file2.toURI().toURL())
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
        return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, classLoader)
    }
}