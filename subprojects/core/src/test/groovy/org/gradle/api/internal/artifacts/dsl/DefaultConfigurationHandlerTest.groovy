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

package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer
import org.gradle.api.internal.ClassGenerator
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.DomainObjectContext

/**
 * @author Hans Dockter
 */

class DefaultConfigurationHandlerTest {
    private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    private IvyService ivyService = context.mock(IvyService)
    private DomainObjectContext domainObjectContext = context.mock(DomainObjectContext.class)

    private ClassGenerator classGenerator = new AsmBackedClassGenerator()
    private DefaultConfigurationContainer configurationHandler = classGenerator.newInstance(DefaultConfigurationContainer.class, ivyService, classGenerator, { name -> name } as DomainObjectContext)

    @Test
    void addsNewConfigurationWhenConfiguringSelf() {
        configurationHandler.configure {
            newConf
        }
        assertThat(configurationHandler.findByName('newConf'), notNullValue())
        assertThat(configurationHandler.newConf, notNullValue())
    }

    @Test(expected = UnknownConfigurationException)
    void doesNotAddNewConfigurationWhenNotConfiguringSelf() {
        configurationHandler.getByName('unknown')
    }

    @Test
    void makesExistingConfigurationAvailableAsProperty() {
        Configuration configuration = configurationHandler.add('newConf')
        assertThat(configuration, is(not(null)))
        assertThat(configurationHandler.getByName("newConf"), sameInstance(configuration))
        assertThat(configurationHandler.newConf, sameInstance(configuration))
    }

    @Test
    void addsNewConfigurationWithClosureWhenConfiguringSelf() {
        String someDesc = 'desc1'
        configurationHandler.configure {
            newConf {
                description = someDesc
            }
        }
        assertThat(configurationHandler.newConf.getDescription(), equalTo(someDesc))
    }

    @Test
    void makesExistingConfigurationAvailableAsConfigureMethod() {
        String someDesc = 'desc1'
        configurationHandler.add('newConf')
        Configuration configuration = configurationHandler.newConf {
            description = someDesc
        }
        assertThat(configuration.getDescription(), equalTo(someDesc))
    }

    @Test
    void makesExistingConfigurationAvailableAsConfigureMethodWhenConfiguringSelf() {
        String someDesc = 'desc1'
        Configuration configuration = configurationHandler.add('newConf')
        configurationHandler.configure {
            newConf {
                description = someDesc
            }
        }
        assertThat(configuration.getDescription(), equalTo(someDesc))
    }

    @Test(expected = MissingMethodException)
    void newConfigurationWithNonClosureParametersShouldThrowMissingMethodEx() {
        configurationHandler.newConf('a', 'b')
    }
}
