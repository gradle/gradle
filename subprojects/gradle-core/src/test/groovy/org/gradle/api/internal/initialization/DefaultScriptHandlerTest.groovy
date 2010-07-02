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
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.WrapUtil
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.util.ObservableUrlClassLoader

@RunWith(JMock)
public class DefaultScriptHandlerTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class)
    private final DependencyHandler dependencyHandler = context.mock(DependencyHandler.class)
    private final ConfigurationContainer configurationContainer = context.mock(ConfigurationContainer.class)
    private final Configuration configuration = context.mock(Configuration.class)
    private final ScriptSource scriptSource = context.mock(ScriptSource.class)
    private final ObservableUrlClassLoader classLoader = context.mock(ObservableUrlClassLoader.class)

    @Test void addsClasspathConfiguration() {
        context.checking {
            one(configurationContainer).add('classpath')
        }

        new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, classLoader)
    }

    @Test void createsAClassLoaderAndAddsContentsOfClassPathConfiguration() {
        DefaultScriptHandler handler = handler()

        ClassLoader classLoader = handler.classLoader
        assertThat(classLoader, sameInstance(this.classLoader))

        File file1 = new File('a')
        File file2 = new File('b')
        context.checking {
            one(configuration).getFiles()
            will(returnValue(WrapUtil.toSet(file1, file2)))
            one(classLoader).addURL(file1.toURI().toURL())
            one(classLoader).addURL(file2.toURI().toURL())
        }

        handler.updateClassPath()
    }

    @Test void canConfigureRepositories() {
        DefaultScriptHandler handler = handler()

        context.checking {
            one(repositoryHandler).mavenCentral()
        }

        handler.repositories {
            mavenCentral()
        }
    }

    @Test void canConfigureDependencies() {
        DefaultScriptHandler handler = handler()

        context.checking {
            one(dependencyHandler).add('config', 'dep')
        }

        handler.dependencies {
            add('config', 'dep')
        }
    }

    private DefaultScriptHandler handler() {
        context.checking {
            one(configurationContainer).add('classpath')
            will(returnValue(configuration))
        }
        return new DefaultScriptHandler(scriptSource, repositoryHandler, dependencyHandler, configurationContainer, classLoader)
    }
}