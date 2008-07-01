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

package org.gradle.api.internal.dependencies;

import groovy.mock.interceptor.MockFor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.dependencies.ClientModule
import org.gradle.api.dependencies.Dependency
import org.gradle.api.dependencies.ModuleDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
// todo: remove AbstractDependencyContainerTest. prefix for the consts when the Groovy bug is fixed
abstract class AbstractDependencyContainerTest extends GroovyTestCase {
    static final String TEST_CONFIGURATION = 'testConfig'
    static final String DEFAULT_CONFIGURATION = 'testConfig'

    static final String TEST_DEPENDENCY_1 = 'junit:junit:3.8.2@jar'
    static final String TEST_DEPENDENCY_2 = 'log4j:log4j:1.2.9'
    static final String TEST_DEPENDENCY_3 = 'spring:spring:2.0.0'
    static final String TEST_DEPENDENCY_4 = 'spring:spring-mock:2.1'
    static final List TEST_DEPENDENCIES = [TEST_DEPENDENCY_1, TEST_DEPENDENCY_2, TEST_DEPENDENCY_3, TEST_DEPENDENCY_4]

    protected DependencyFactory dependencyFactory
    protected DefaultProject project
    protected File projectRootDir

    protected List testDefaultConfs
    protected List testConfs

    abstract DefaultDependencyContainer getTestObj()

    void setUp() {
        projectRootDir = new File('path', 'root')
        project = HelperUtil.createRootProject(projectRootDir)
        project.gradleUserHome = 'gradleUserHome'
        dependencyFactory = new DependencyFactory()
        testDefaultConfs = [DEFAULT_CONFIGURATION]
        testConfs = [TEST_CONFIGURATION]
    }

    void testDependencyContainerInit() {
        assert testObj.project.is(project)
        assert testObj.dependencyFactory.is(dependencyFactory)
        assertEquals([DEFAULT_CONFIGURATION], new DefaultDependencyContainer(dependencyFactory, [DEFAULT_CONFIGURATION]).defaultConfs)
    }

    void testAddDepencenciesWithConfiguration() {
        checkAddDependencies(testConfs, {List configurations, Object[] dependencies ->
            testObj.dependencies(configurations, dependencies)
        })
    }

    void testAddDepencencies() {
        checkAddDependencies(testDefaultConfs, {List configurations, Object[] dependencies ->
            testObj.dependencies(dependencies)
        })
    }

    private void checkAddDependencies(List expectedConfigurations, Closure addDependencyMethod) {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency, [:] as Dependency]
        4.times {int i ->
            dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependency, DefaultProject project ->
                assertEquals(expectedConfigurations as Set, confs)
                assert userDependency.is(AbstractDependencyContainerTest.TEST_DEPENDENCIES[i])
                assert project.is(this.project)
                dependencies[i]
            }
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            testObj.dependencies(expectedConfigurations,
                    AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                    AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
            assertEquals(dependencies[0..1], testObj.dependencies)
            addDependencyMethod(expectedConfigurations,
                    [AbstractDependencyContainerTest.TEST_DEPENDENCY_3, AbstractDependencyContainerTest.TEST_DEPENDENCY_4])
            assertEquals(dependencies[0..3], testObj.dependencies)
        }
    }

    void testAddDependencyDescriptor() {
        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        testObj.dependencyDescriptors(dependencyDescriptor)
        assertEquals([dependencyDescriptor], testObj.dependencyDescriptors)
        DependencyDescriptor dependencyDescriptor2 = [:] as DependencyDescriptor
        testObj.dependencyDescriptors(dependencyDescriptor2)
        assertEquals([dependencyDescriptor, dependencyDescriptor2], testObj.dependencyDescriptors)
    }

    void testAddDepencencyWithConfiguration() {
        checkAddDependency(testConfs, {List configurations, String dependency, Closure cl ->
            testObj.dependency(configurations, dependency, cl)
        })
    }

    void testAddDependency() {
        checkAddDependency(testDefaultConfs, {List configurations, String dependency, Closure cl  ->
            testObj.dependency(dependency, cl)
        })
    }

    private void checkAddDependency(List expectedConfs, Closure addDependencyMethod) {
        MockFor dependencyFactoryMocker = new MockFor(DependencyFactory)
        String expectedId = 'someid'

        ModuleDependency testModuleDependency = new ModuleDependency('org:name:1.0')
        dependencyFactoryMocker.demand.createDependency(1..1) {Set confs, Object userDependencyDescriptor, DefaultProject project ->
            assertEquals(expectedConfs as Set, confs)
            assert userDependencyDescriptor.is(expectedId)
            testModuleDependency
        }
        dependencyFactoryMocker.use(dependencyFactory) {
            ModuleDependency moduleDependency = addDependencyMethod(expectedConfs, expectedId) {
                exclude([:])
            }
            assert moduleDependency.is(testModuleDependency)
            assert moduleDependency.is(testObj.dependencies[0])
            assert moduleDependency.excludeRules
        }
    }

    void testAddClientModuleWithConfigurations() {
        checkAddClientModule(testConfs, {List configurations, String dependency, Closure configureClosure ->
            testObj.clientModule(configurations, dependency, configureClosure)
        })
    }

    void testAddClientModule() {
        checkAddClientModule(testDefaultConfs, {List configurations, String dependency, Closure configureClosure ->
            testObj.clientModule(dependency, configureClosure)
        })
    }

    private void checkAddClientModule(List expectedConfs, Closure addDependencyMethod) {
        String expectedId = 'someid'
        DependencyFactory testDependencyFactory = [:] as DependencyFactory
        ClientModule clientModule = addDependencyMethod(expectedConfs, expectedId) {
            dependencyFactory = testDependencyFactory
        }
        assertEquals(expectedConfs as Set, clientModule.confs)
        assertEquals(expectedId, clientModule.id)
        assert clientModule.dependencyFactory.is(testDependencyFactory)
    }
}
