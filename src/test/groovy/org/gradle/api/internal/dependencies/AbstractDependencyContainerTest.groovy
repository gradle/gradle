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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.Project
import org.gradle.api.dependencies.*
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser
import static org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @author Hans Dockter
 */
// todo: remove AbstractDependencyContainerTest. prefix for the consts when the Groovy bug is fixed
@RunWith (org.jmock.integration.junit4.JMock)
public abstract class AbstractDependencyContainerTest {
    static final String TEST_CONFIGURATION = 'testConfig'
    static final String DEFAULT_CONFIGURATION = 'testConfig'

    static final String TEST_DEPENDENCY_1 = 'junit:junit:3.8.2@jar'
    static final String TEST_DEPENDENCY_2 = 'log4j:log4j:1.2.9'
    static final String TEST_DEPENDENCY_3 = 'spring:spring:2.0.0'
    static final String TEST_DEPENDENCY_4 = 'spring:spring-mock:2.1'
    static final List TEST_DEPENDENCIES = [TEST_DEPENDENCY_1, TEST_DEPENDENCY_2, TEST_DEPENDENCY_3, TEST_DEPENDENCY_4]

    protected DefaultModuleDependency filterTestLibDependency;
    protected DefaultProjectDependency filterTestProjectDependency;

    protected DependencyFactory dependencyFactory
    protected Project project
    protected File projectRootDir

    protected List testDefaultConfs
    protected List testConfs

    public abstract DefaultDependencyContainer getTestObj()

    protected JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE)
        projectRootDir = new File('path', 'root')
        project = HelperUtil.createRootProject(projectRootDir)
        project.gradleUserHome = 'gradleUserHome'
        dependencyFactory = context.mock(DependencyFactory)
        testDefaultConfs = [DEFAULT_CONFIGURATION]
        testConfs = [TEST_CONFIGURATION]
        filterTestLibDependency = context.mock(DefaultModuleDependency)
        filterTestProjectDependency = context.mock(DefaultProjectDependency)
    }

    @Test public void testDependencyContainerInit() {
        assert testObj.project.is(project)
        assert testObj.dependencyFactory.is(dependencyFactory)
        assertEquals([DEFAULT_CONFIGURATION], new DefaultDependencyContainer(dependencyFactory, [DEFAULT_CONFIGURATION]).defaultConfs)
    }

    @Test public void testAddDepencenciesWithConfiguration() {
        checkAddDependencies(testConfs, {List configurations, Object[] dependencies ->
            testObj.dependencies(configurations, dependencies)
        })
    }

    @Test public void testAddDepencencies() {
        checkAddDependencies(testDefaultConfs, {List configurations, Object[] dependencies ->
            testObj.dependencies(dependencies)
        })
    }

    private void checkAddDependencies(List expectedConfigurations, Closure addDependencyMethod) {
        List dependencies = [[:] as Dependency, [:] as Dependency, [:] as Dependency, [:] as Dependency]
        context.checking {
            4.times {int i ->
                one(dependencyFactory).createDependency(expectedConfigurations as Set,
                        AbstractDependencyContainerTest.TEST_DEPENDENCIES[i],
                        project); will(returnValue(dependencies[i]));
            }
        }
        testObj.dependencies(expectedConfigurations,
                AbstractDependencyContainerTest.TEST_DEPENDENCY_1,
                AbstractDependencyContainerTest.TEST_DEPENDENCY_2)
        assertEquals(dependencies[0..1], testObj.dependencies)
        addDependencyMethod(expectedConfigurations,
                [AbstractDependencyContainerTest.TEST_DEPENDENCY_3, AbstractDependencyContainerTest.TEST_DEPENDENCY_4])
        assertEquals(dependencies[0..3], testObj.dependencies)
    }

    @Test public void testAddDependencyDescriptor() {
        DependencyDescriptor dependencyDescriptor = [:] as DependencyDescriptor
        testObj.dependencyDescriptors(dependencyDescriptor)
        assertEquals([dependencyDescriptor], testObj.dependencyDescriptors)
        DependencyDescriptor dependencyDescriptor2 = [:] as DependencyDescriptor
        testObj.dependencyDescriptors(dependencyDescriptor2)
        assertEquals([dependencyDescriptor, dependencyDescriptor2], testObj.dependencyDescriptors)
    }

    @Test public void testAddDepencencyWithConfiguration() {
        checkAddDependency(testConfs, {List configurations, String dependency, Closure cl ->
            testObj.dependency(configurations, dependency, cl)
        })
    }

    @Test public void testAddDependency() {
        checkAddDependency(testDefaultConfs, {List configurations, String dependency, Closure cl ->
            testObj.dependency(dependency, cl)
        })
    }

    @Test public void testGetDependenciesWithLibsFilter() {
        addFilterTestDependencies()
        assertEquals([filterTestLibDependency], testObj.getDependencies(Filter.LIBS_ONLY))
    }

    @Test public void testGetDependenciesWithProjectFilter() {
        addFilterTestDependencies()
        assertEquals([filterTestProjectDependency], testObj.getDependencies(Filter.PROJECTS_ONLY))
    }

    @Test public void testGetDependenciesWithNoFilter() {
        addFilterTestDependencies()
        assertEquals([filterTestProjectDependency, filterTestLibDependency] as Set, testObj.getDependencies(Filter.NONE) as Set)
    }

    private void addFilterTestDependencies() {
        testObj.getDependencies().add(filterTestLibDependency)
        testObj.getDependencies().add(filterTestProjectDependency)
    }

    private void checkAddDependency(List expectedConfs, Closure addDependencyMethod) {
        String expectedId = 'someid'

        DefaultModuleDependency testModuleDependency = new DefaultModuleDependency([] as Set, 'org:name:1.0')

        context.checking {
            one(dependencyFactory).createDependency(expectedConfs as Set,
                    expectedId,
                    project); will(returnValue(testModuleDependency));
        }
        DefaultModuleDependency moduleDependency = addDependencyMethod(expectedConfs, expectedId) {
            exclude([:])
        }
        assert moduleDependency.is(testModuleDependency)
        assert moduleDependency.is(testObj.dependencies[0])
        assert moduleDependency.excludeRules
    }

    @Test public void testAddClientModuleWithConfigurations() {
        checkAddClientModule(testConfs, {List configurations, String dependency, Closure configureClosure ->
            testObj.clientModule(configurations, dependency, configureClosure)
        })
    }

    @Test public void testAddClientModule() {
        checkAddClientModule(testDefaultConfs, {List configurations, String dependency, Closure configureClosure ->
            testObj.clientModule(dependency, configureClosure)
        })
    }

    private void checkAddClientModule(List expectedConfs, Closure addDependencyMethod) {
        String expectedId = 'org:name:1.2'
        DependencyFactory testDependencyFactory = [:] as DependencyFactory
        ClientModule clientModule = addDependencyMethod(expectedConfs, expectedId) {
            dependencyFactory = testDependencyFactory
        }
        assertEquals(expectedConfs as Set, clientModule.getDependencyConfigurationMappings().getMasterConfigurations())
        assertEquals(expectedId, clientModule.id)
        assert clientModule.dependencyFactory.is(testDependencyFactory)
    }
}
