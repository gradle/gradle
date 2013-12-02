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
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.ClassLoaderVisitor
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.util.ConfigureUtil
import spock.lang.Specification

class DefaultScriptHandlerTest extends Specification {
    def repositoryHandler = Mock(RepositoryHandler)
    def dependencyHandler = Mock(DependencyHandler)
    def configurationContainer = Mock(ConfigurationContainer)
    def configuration = Stub(Configuration)
    def scriptSource = Stub(ScriptSource)
    def baseClassLoader = new ClassLoader() {}
    def parentScope = Stub(ScriptCompileScope) {
        getScriptCompileClassLoader() >> baseClassLoader
    }

    def "adds classpath configuration"() {
        when:
        new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, parentScope)

        then:
        1 * configurationContainer.create('classpath')
    }

    def "uses base class loader when classpath configuration is empty and no parents declared"() {
        def handler = handler()

        given:
        configuration.files >> []

        when:
        handler.updateClassPath()
        def classLoader = handler.classLoader

        then:
        classLoader == baseClassLoader
    }

    def "creates a class loader when classpath configuration is not empty and no parents declared"() {
        def handler = handler()
        def file1 = new File('a')
        def file2 = new File('b')

        given:
        configuration.files >> [file1, file2]

        when:
        handler.updateClassPath()
        def classLoader = handler.classLoader

        then:
        classLoader instanceof MutableURLClassLoader
        classLoader.parent == baseClassLoader
        classLoader.URLs == [file1.toURI().toURL(), file2.toURI().toURL()] as URL[]
    }

    def "creates a class loader when classpath configuration is empty and parents declared"() {
        def handler = handler()
        def parent = Mock(ClassLoader)
        def visitor = Mock(ClassLoaderVisitor)

        given:
        configuration.files >> []
        handler.addParent(parent)

        when:
        handler.updateClassPath()
        def classLoader = handler.classLoader

        then:
        classLoader instanceof CachingClassLoader
        classLoader.parent instanceof MultiParentClassLoader

        when:
        classLoader.parent.visit(visitor)

        then:
        1 * visitor.visitParent(baseClassLoader)
        1 * visitor.visitParent(parent)
    }

    def "creates a class loader when classpath configuration is not empty and parents declared"() {
        def handler = handler()
        def parent = Mock(ClassLoader)
        def visitor = Mock(ClassLoaderVisitor)
        def file1 = new File('a')
        def file2 = new File('b')

        given:
        configuration.files >> [file1, file2]
        handler.addParent(parent)

        when:
        handler.updateClassPath()
        def classLoader = handler.classLoader

        then:
        classLoader instanceof CachingClassLoader
        classLoader.parent instanceof MultiParentClassLoader

        when:
        classLoader.parent.visit(visitor)

        then:
        1 * visitor.visitParent({ it instanceof MutableURLClassLoader }) >> { ClassLoader cl ->
            assert cl.parent == baseClassLoader
            assert cl.URLs == [file1.toURI().toURL(), file2.toURI().toURL()] as URL[]
        }
        1 * visitor.visitParent(parent)
    }

    def "creates script class loader on demand when not finalized and fills in the missing pieces once finalized"() {
        def handler = handler()
        def visitor = Mock(ClassLoaderVisitor)
        def parent = Mock(ClassLoader)
        def file1 = new File('a')
        def file2 = new File('b')

        given:
        configuration.files >> [file1, file2]

        when:
        def classLoader = handler.classLoader

        then:
        classLoader instanceof CachingClassLoader
        classLoader.parent instanceof MultiParentClassLoader

        when:
        handler.addParent(parent)
        handler.updateClassPath()
        classLoader.parent.visit(visitor)

        then:
        1 * visitor.visitParent({ it instanceof MutableURLClassLoader }) >> { ClassLoader cl ->
            assert cl.parent == baseClassLoader
            assert cl.URLs == [file1.toURI().toURL(), file2.toURI().toURL()] as URL[]
        }
        1 * visitor.visitParent(parent)
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
        return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, parentScope)
    }
}