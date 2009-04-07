/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.configurations.ResolverProvider
import org.gradle.api.internal.artifacts.dsl.DefaultConfigurationHandler
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Assert
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */

class DefaultConfigurationHandlerTest {
  private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

  private IvyService ivyService = context.mock(IvyService)
  private ResolverProvider resolverProvider = context.mock(ResolverProvider)
  private DependencyMetaDataProvider dependencyMetaDataProvider = context.mock(DependencyMetaDataProvider)

  private DefaultConfigurationHandler configurationHandler = new DefaultConfigurationHandler(ivyService,
          resolverProvider, dependencyMetaDataProvider)

  @Test void newAndExisitingConfiguration() {
    Configuration configuration = configurationHandler.newConf
    assertThat(configuration, is(not(null)))
    assertThat(configurationHandler.get("newConf"), sameInstance(configuration))
    assertThat(configurationHandler.newConf, sameInstance(configuration))
  }

  @Test void newConfigurationWithClosure() {
    String someDesc = 'desc1'
    Configuration configuration = configurationHandler.newConf {
      description = someDesc
    }
    assertThat(configuration.getDescription(), equalTo(someDesc))
  }

  @Test void existingConfigurationWithClosure() {
    String someDesc = 'desc1'
    configurationHandler.newConf
    Configuration configuration = configurationHandler.newConf {
      description = someDesc
    }
    assertThat(configuration.getDescription(), equalTo(someDesc))
  }

  @Test(expected = MissingMethodException)
  void newConfigurationWithNonClosureParameters_shouldThrowMissingMethodEx() {
    configurationHandler.newConf('a', 'b')
  }


}
