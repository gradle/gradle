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
package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.util.ConfigureUtil
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.artifacts.*
import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.sameInstance
import static org.junit.Assert.assertThat

/**
 * @author Hans Dockter
 */
@RunWith (org.jmock.integration.junit4.JMock)
class DefaultDependencyHandlerTest {
    private static final String TEST_CONF_NAME = "someConf"

    private JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    private ConfigurationContainer configurationContainerStub = context.mock(ConfigurationContainer)
    private DependencyFactory dependencyFactoryStub = context.mock(DependencyFactory)
    private Configuration configurationMock = context.mock(Configuration)
    private ProjectFinder projectFinderDummy = context.mock(ProjectFinder)

    private DefaultDependencyHandler dependencyHandler = new DefaultDependencyHandler(
            configurationContainerStub, dependencyFactoryStub, projectFinderDummy)

    @Before
    void setUp() {
        context.checking {
            allowing(configurationContainerStub).findByName(TEST_CONF_NAME); will(returnValue(configurationMock))
        }
    }

    @Test
    void add() {
        String someNotation = "someNotation"
        Dependency dependencyDummy = context.mock(Dependency)
        context.checking {
            allowing(configurationContainerStub).getAt(TEST_CONF_NAME); will(returnValue(configurationMock))
            allowing(dependencyFactoryStub).createDependency(someNotation); will(returnValue(dependencyDummy))
            one(configurationMock).addDependency(dependencyDummy);
        }

        assertThat(dependencyHandler.add(TEST_CONF_NAME, someNotation), equalTo(dependencyDummy))
    }

    @Test
    void addWithClosure() {
        String someNotation = "someNotation"
        def closure = { }
        DefaultExternalModuleDependency returnedDependency = HelperUtil.createDependency("group", "name", "1.0")
        context.checking {
            allowing(configurationContainerStub).getAt(TEST_CONF_NAME); will(returnValue(configurationMock))
            allowing(dependencyFactoryStub).createDependency(someNotation); will(returnValue(returnedDependency))
            one(configurationMock).addDependency(returnedDependency);
        }
        def dependency = dependencyHandler.add(TEST_CONF_NAME, someNotation) {
            force = true    
        }
        assertThat(dependency, equalTo(returnedDependency))
        assertThat(dependency.force, equalTo(true))
    }

    @Test
    void pushOneDependency() {
        String someNotation = "someNotation"
        Dependency dependencyDummy = context.mock(Dependency)
        context.checking {
            allowing(dependencyFactoryStub).createDependency(someNotation); will(returnValue(dependencyDummy))
            one(configurationMock).addDependency(dependencyDummy);
        }

        assertThat(dependencyHandler."$TEST_CONF_NAME"(someNotation), equalTo(dependencyDummy))
    }

    @Test
    void pushOneDependencyWithClosure() {
        String someNotation = "someNotation"
        DefaultExternalModuleDependency returnedDependency = HelperUtil.createDependency("group", "name", "1.0")
        context.checking {
            allowing(dependencyFactoryStub).createDependency(someNotation); will(returnValue(returnedDependency))
            one(configurationMock).addDependency(returnedDependency);
        }

        def dependency = dependencyHandler."$TEST_CONF_NAME"(someNotation) {
            force = true
        }
        assertThat(dependency, equalTo(returnedDependency))
        assertThat(dependency.force, equalTo(true))
    }

    @Test
    void pushMultipleDependencies() {
        String someNotation1 = "someNotation"
        Map someNotation2 = [a: 'b', c: 'd']
        Dependency dependencyDummy1 = context.mock(Dependency, "dep1")
        Dependency dependencyDummy2 = context.mock(Dependency, "dep2")
        context.checking {
            allowing(dependencyFactoryStub).createDependency(someNotation1); will(returnValue(dependencyDummy1))
            allowing(dependencyFactoryStub).createDependency(someNotation2); will(returnValue(dependencyDummy2))
            one(configurationMock).addDependency(dependencyDummy1);
            one(configurationMock).addDependency(dependencyDummy2);
        }

        dependencyHandler."$TEST_CONF_NAME"(someNotation1, someNotation2)
    }

    @Test
    void pushMultipleDependenciesViaNestedList() {
        String someNotation1 = "someNotation"
        Map someNotation2 = [a: 'b', c: 'd']
        Dependency dependencyDummy1 = context.mock(Dependency, "dep1")
        Dependency dependencyDummy2 = context.mock(Dependency, "dep2")
        context.checking {
            allowing(dependencyFactoryStub).createDependency(someNotation1); will(returnValue(dependencyDummy1))
            allowing(dependencyFactoryStub).createDependency(someNotation2); will(returnValue(dependencyDummy2))
            one(configurationMock).addDependency(dependencyDummy1);
            one(configurationMock).addDependency(dependencyDummy2);
        }

        dependencyHandler."$TEST_CONF_NAME"([[someNotation1, someNotation2]])
    }

    @Test
    void pushProjectByMap() {
        ProjectDependency projectDependency = context.mock(ProjectDependency)
        Map someMapNotation = [:]
        Closure projectDependencyClosure = {
            assertThat("$TEST_CONF_NAME"(project(someMapNotation)), equalTo(projectDependency))
        }
        context.checking {
            allowing(dependencyFactoryStub).createProjectDependencyFromMap(projectFinderDummy, someMapNotation); will(returnValue(projectDependency))
            allowing(dependencyFactoryStub).createDependency(projectDependency); will(returnValue(projectDependency))
            one(configurationMock).addDependency(projectDependency);
        }

        ConfigureUtil.configure(projectDependencyClosure, dependencyHandler)
    }

    @Test
    void pushProjectByMapWithConfigureClosure() {
        ProjectDependency projectDependency = context.mock(ProjectDependency)
        Map someMapNotation = [:]
        Closure projectDependencyClosure = {
            def dependency = "$TEST_CONF_NAME"(project(someMapNotation)) {
                copy()    
            }
            assertThat(dependency, equalTo(projectDependency))
        }
        context.checking {
            allowing(dependencyFactoryStub).createProjectDependencyFromMap(projectFinderDummy, someMapNotation); will(returnValue(projectDependency))
            allowing(dependencyFactoryStub).createDependency(projectDependency); will(returnValue(projectDependency))
            one(configurationMock).addDependency(projectDependency);
            one(projectDependency).copy();
        }

        ConfigureUtil.configure(projectDependencyClosure, dependencyHandler)
    }

    @Test
    void pushModule() {
        ClientModule clientModule = context.mock(ClientModule)
        String someNotation = "someNotation"
        Closure moduleClosure = {
            assertThat("$TEST_CONF_NAME"(module(someNotation)), equalTo(clientModule))
        }
        context.checking {
            allowing(dependencyFactoryStub).createModule(someNotation, null); will(returnValue(clientModule))
            allowing(dependencyFactoryStub).createDependency(clientModule); will(returnValue(clientModule))
            one(configurationMock).addDependency(clientModule);
        }

        ConfigureUtil.configure(moduleClosure, dependencyHandler)
    }

    @Test
    void pushModuleWithConfigureClosure() {
        ClientModule clientModule = context.mock(ClientModule)
        String someNotation = "someNotation"
        Closure configureClosure = {}
        Closure moduleClosure = {
            assertThat("$TEST_CONF_NAME"(module(someNotation, configureClosure)), equalTo(clientModule))
        }
        context.checking {
            allowing(dependencyFactoryStub).createModule(someNotation, configureClosure); will(returnValue(clientModule))
            allowing(dependencyFactoryStub).createDependency(clientModule); will(returnValue(clientModule))
            one(configurationMock).addDependency(clientModule);
        }

        ConfigureUtil.configure(moduleClosure, dependencyHandler)
    }

    @Test
    void pushGradleApi() {
        Dependency dependency = context.mock(Dependency)
        context.checking {
            one(dependencyFactoryStub).createDependency(DependencyFactory.ClassPathNotation.GRADLE_API)
            will(returnValue(dependency))

            one(dependencyFactoryStub).createDependency(dependency)
            will(returnValue(dependency))

            one(configurationMock).addDependency(dependency)
        }

        Closure moduleClosure = {
            assertThat("$TEST_CONF_NAME"(gradleApi()), sameInstance(dependency))
        }
        ConfigureUtil.configure(moduleClosure, dependencyHandler)
    }

    @Test
    void pushLocalGroovy() {
        Dependency dependency = context.mock(Dependency)
        context.checking {
            one(dependencyFactoryStub).createDependency(DependencyFactory.ClassPathNotation.LOCAL_GROOVY)
            will(returnValue(dependency))

            one(dependencyFactoryStub).createDependency(dependency)
            will(returnValue(dependency))

            one(configurationMock).addDependency(dependency)
        }

        Closure moduleClosure = {
            assertThat("$TEST_CONF_NAME"(localGroovy()), sameInstance(dependency))
        }
        ConfigureUtil.configure(moduleClosure, dependencyHandler)
    }
    
    @Test (expected = MissingMethodException)
    void pushToUnknownConfiguration() {
        String unknownConf = TEST_CONF_NAME + "delta"
        context.checking {
            allowing(configurationContainerStub).findByName(unknownConf); will(returnValue(null))
        }
        dependencyHandler."$unknownConf"("someNotation")
    }

}
