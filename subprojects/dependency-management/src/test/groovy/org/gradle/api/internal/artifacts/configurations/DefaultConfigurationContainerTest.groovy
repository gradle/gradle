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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.MissingMethodException
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

@RunWith(JMock)
class DefaultConfigurationContainerTest {
    private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    private ConfigurationResolver resolver = context.mock(ConfigurationResolver)
    private ListenerManager listenerManager = context.mock(ListenerManager.class)
    private DependencyMetaDataProvider metaDataProvider = context.mock(DependencyMetaDataProvider.class)
    private ProjectAccessListener projectAccessListener = context.mock(ProjectAccessListener.class)
    private Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), DirectInstantiator.INSTANCE)
    private DefaultConfigurationContainer configurationContainer = instantiator.newInstance(DefaultConfigurationContainer.class,
            resolver, instantiator, { name -> name } as DomainObjectContext,
            listenerManager, metaDataProvider, projectAccessListener, context.mock(ProjectFinder))

    @Before
    public void setup() {
        context.checking {
            ignoring(listenerManager)
        }
    }

    @Test
    void addsNewConfigurationWhenConfiguringSelf() {
        configurationContainer.configure {
            newConf
        }
        assertThat(configurationContainer.findByName('newConf'), notNullValue())
        assertThat(configurationContainer.newConf, notNullValue())
    }

    @Test(expected = UnknownConfigurationException)
    void doesNotAddNewConfigurationWhenNotConfiguringSelf() {
        configurationContainer.getByName('unknown')
    }

    @Test
    void makesExistingConfigurationAvailableAsProperty() {
        Configuration configuration = configurationContainer.create('newConf')
        assertThat(configuration, notNullValue())
        assertThat(configurationContainer.getByName("newConf"), sameInstance(configuration))
        assertThat(configurationContainer.newConf, sameInstance(configuration))
    }

    @Test
    void addsNewConfigurationWithClosureWhenConfiguringSelf() {
        String someDesc = 'desc1'
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }
        assertThat(configurationContainer.newConf.getDescription(), equalTo(someDesc))
    }

    @Test
    void makesExistingConfigurationAvailableAsConfigureMethod() {
        String someDesc = 'desc1'
        configurationContainer.create('newConf')
        Configuration configuration = configurationContainer.newConf {
            description = someDesc
        }
        assertThat(configuration.getDescription(), equalTo(someDesc))
    }

    @Test
    void makesExistingConfigurationAvailableAsConfigureMethodWhenConfiguringSelf() {
        String someDesc = 'desc1'
        Configuration configuration = configurationContainer.create('newConf')
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }
        assertThat(configuration.getDescription(), equalTo(someDesc))
    }

    @Test(expected = MissingMethodException)
    void newConfigurationWithNonClosureParametersShouldThrowMissingMethodEx() {
        configurationContainer.newConf('a', 'b')
    }
}
