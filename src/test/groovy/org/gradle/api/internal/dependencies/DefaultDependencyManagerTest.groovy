/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.dependencies

import org.gradle.api.dependencies.Configuration
import org.gradle.api.dependencies.Dependency
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
public class DefaultDependencyManagerTest extends BaseDependencyManagerTest {
    static final String TEST_CONFIG = 'testConfig';
    Configuration testConfiguration = [:] as Configuration
    Closure testClosure = {}

    private DefaultDependencyManager dependencyManager = new DefaultDependencyManager(
            project, dependencyContainerMock, artifactContainerMock, configurationContainerMock,
            configurationResolverFactoryMock, dependencyResolversMock, resolverFactoryMock, buildResolverHandler, ivyServiceMock
    );

    protected BaseDependencyManager getDependencyManager() {
        return dependencyManager
    }

    @Before
    public void setUp() {
        super.setUp()
        context.checking {
            allowing(configurationContainerMock).find(TEST_CONFIG); will(returnValue(testConfiguration))
            allowing(configurationResolverFactoryMock).createConfigurationResolver(testConfiguration,
                    dependencyContainerMock,
                    dependencyResolversMock,
                    artifactContainerMock,
                    configurationContainerMock); will(returnValue(testConfigurationResolver))
        }
    }

    @Test public void testDynamicPropertyToReturnConfiguration() {
        assertThat(dependencyManager."$TEST_CONFIG", sameInstance(testConfigurationResolver))
    }

    @Test (expected = MissingPropertyException)
    public void testPropertyMissingWithNonExistingConfiguration() {
        String unknownConfig = TEST_CONFIG + "delta"
        context.checking {
            allowing(configurationContainerMock).find(unknownConfig); will(returnValue(null))
        }
        dependencyManager."$unknownConfig"
    }

    @Test (expected = MissingMethodException)
    public void testMethodMissingWithNonExistingConfiguration() {
        String unknownConfig = TEST_CONFIG + "delta"
        context.checking {
            allowing(configurationContainerMock).find(unknownConfig); will(returnValue(null))
        }
        dependencyManager."$unknownConfig"("dep1", "dep2")
    }

    @Test public void testDynamicMethodToConfigureConfiguration() {
        context.checking {
            one(configurationContainerMock).get(TEST_CONFIG, testClosure); will(returnValue(testConfiguration))
        }
        dependencyManager."$TEST_CONFIG" (testClosure)
    }

    @Test public void testDynamicMethodForAddDependencyWithClosure() {
        Object testDependencyDescription = new Object();
        Dependency testDependency = [:] as Dependency
        context.checking {
            one(dependencyContainerMock).dependency([TEST_CONFIG], testDependencyDescription, testClosure)
            will(returnValue(testDependency))
        }
        assertThat(testDependency, sameInstance(dependencyManager."$TEST_CONFIG"(testDependencyDescription, testClosure)))
    }

    @Test public void testDynamicMethodForAddDependencies() {
        Object testDependencyDescription1 = new Object();
        Object testDependencyDescription2 = new Object();
        context.checking {
            one(dependencyContainerMock).dependencies([TEST_CONFIG], [testDependencyDescription1, testDependencyDescription2] as Object[])
        }
        dependencyManager."$TEST_CONFIG"(testDependencyDescription1, testDependencyDescription2)
    }


}
